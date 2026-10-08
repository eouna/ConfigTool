#pragma once

#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

#include "CfgDefs.h"
#include "miniz.h"

namespace cfg {

/// 追加UTF-8字符
inline void AppendUtf8(std::string& out, unsigned int code) {
    if (code < 0x80) {
        out.push_back(static_cast<char>(code));
    } else if (code < 0x800) {
        out.push_back(static_cast<char>(0xC0 | (code >> 6)));
        out.push_back(static_cast<char>(0x80 | (code & 0x3F)));
    } else if (code < 0x10000) {
        out.push_back(static_cast<char>(0xE0 | (code >> 12)));
        out.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (code & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (code >> 18)));
        out.push_back(static_cast<char>(0x80 | ((code >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (code & 0x3F)));
    }
}

/// 解码XML实体
inline std::string DecodeXml(const std::string& value) {
    std::string result;
    result.reserve(value.size());
    for (std::size_t i = 0; i < value.size(); ++i) {
        if (value[i] != '&') {
            result.push_back(value[i]);
            continue;
        }
        std::size_t end = value.find(';', i);
        if (end == std::string::npos) {
            result.push_back(value[i]);
            continue;
        }
        std::string entity = value.substr(i + 1, end - i - 1);
        if (entity == "amp") {
            result.push_back('&');
        } else if (entity == "lt") {
            result.push_back('<');
        } else if (entity == "gt") {
            result.push_back('>');
        } else if (entity == "quot") {
            result.push_back('"');
        } else if (entity == "apos") {
            result.push_back('\'');
        } else if (!entity.empty() && entity[0] == '#') {
            unsigned int code = 0;
            if (entity.size() > 1 && (entity[1] == 'x' || entity[1] == 'X')) {
                code = static_cast<unsigned int>(std::strtoul(entity.c_str() + 2, nullptr, 16));
            } else {
                code = static_cast<unsigned int>(std::strtoul(entity.c_str() + 1, nullptr, 10));
            }
            AppendUtf8(result, code);
        } else {
            result += value.substr(i, end - i + 1);
        }
        i = end;
    }
    return result;
}

/// 取属性值
inline std::string XmlAttr(const std::string& tag, const std::string& name) {
    std::string key = name + "=";
    std::size_t position = tag.find(key);
    while (position != std::string::npos) {
        bool boundary = position == 0 || std::isspace(static_cast<unsigned char>(tag[position - 1])) != 0
                        || tag[position - 1] == '<';
        if (boundary) {
            std::size_t quote = position + key.size();
            if (quote < tag.size() && (tag[quote] == '"' || tag[quote] == '\'')) {
                char quoteChar = tag[quote];
                std::size_t end = tag.find(quoteChar, quote + 1);
                if (end != std::string::npos) {
                    return tag.substr(quote + 1, end - quote - 1);
                }
            }
        }
        position = tag.find(key, position + 1);
    }
    return std::string();
}

/// 取元素文本 <tag>text</tag>
inline std::string XmlElementText(const std::string& block, const std::string& tagName) {
    std::string openTag = "<" + tagName;
    std::size_t open = block.find(openTag);
    if (open == std::string::npos) {
        return std::string();
    }
    std::size_t gt = block.find('>', open);
    if (gt == std::string::npos) {
        return std::string();
    }
    std::string closeTag = "</" + tagName + ">";
    std::size_t close = block.find(closeTag, gt);
    if (close == std::string::npos) {
        return std::string();
    }
    return block.substr(gt + 1, close - gt - 1);
}

/// "B12" -> 1 (0基列下标)
inline int ColumnIndex(const std::string& reference) {
    int value = 0;
    for (char ch : reference) {
        if (ch >= 'A' && ch <= 'Z') {
            value = value * 26 + (ch - 'A' + 1);
        } else if (ch >= 'a' && ch <= 'z') {
            value = value * 26 + (ch - 'a' + 1);
        } else {
            break;
        }
    }
    return value - 1;
}

/// 极简xlsx读取: miniz解压 + XML扫描(仅标准库 + miniz)
class XlsxWorkbook {
 public:
    explicit XlsxWorkbook(const std::string& path) {
        std::memset(&m_archive, 0, sizeof(m_archive));
        if (mz_zip_reader_init_file(&m_archive, path.c_str(), 0) == MZ_FALSE) {
            throw std::runtime_error("打开excel失败: " + path);
        }
        m_open = true;
        m_sharedStrings = ParseSharedStrings(ReadEntry("xl/sharedStrings.xml"));
        LoadSheets();
    }

    ~XlsxWorkbook() { Close(); }

    XlsxWorkbook(const XlsxWorkbook&) = delete;
    XlsxWorkbook& operator=(const XlsxWorkbook&) = delete;

    const std::vector<std::string>& SheetNames() const { return m_sheetNames; }

    bool HasSheet(const std::string& name) const { return m_sheetPaths.find(name) != m_sheetPaths.end(); }

    /// 按名字取工作薄(行 x 列 文本)
    Rows Sheet(const std::string& name) const {
        auto iterator = m_sheetPaths.find(name);
        if (iterator == m_sheetPaths.end()) {
            throw std::runtime_error("找不到工作薄: " + name);
        }
        return ParseSheet(ReadEntry(iterator->second));
    }

    /// 按序号取工作薄(0开始)
    Rows SheetAt(std::size_t index) const {
        if (index >= m_sheetNames.size()) {
            throw std::runtime_error("工作薄下标越界: " + std::to_string(index));
        }
        return Sheet(m_sheetNames[index]);
    }

 private:
    void Close() {
        if (m_open) {
            mz_zip_reader_end(&m_archive);
            m_open = false;
        }
    }

    std::string ReadEntry(const std::string& name) const {
        mz_zip_archive* archive = const_cast<mz_zip_archive*>(&m_archive);
        int fileIndex = mz_zip_reader_locate_file(archive, name.c_str(), nullptr, 0);
        if (fileIndex < 0) {
            return std::string();
        }
        mz_zip_archive_file_stat stat{};
        if (mz_zip_reader_file_stat(archive, fileIndex, &stat) == MZ_FALSE) {
            return std::string();
        }
        std::string data(static_cast<std::size_t>(stat.m_uncomp_size), '\0');
        if (data.empty()) {
            return data;
        }
        if (mz_zip_reader_extract_to_mem(archive, fileIndex, &data[0], data.size(), 0) == MZ_FALSE) {
            return std::string();
        }
        return data;
    }

    static std::vector<std::string> ParseSharedStrings(const std::string& xml) {
        std::vector<std::string> result;
        std::size_t position = 0;
        while (true) {
            std::size_t itemOpen = xml.find("<si", position);
            if (itemOpen == std::string::npos) {
                break;
            }
            std::size_t itemClose = xml.find("</si>", itemOpen);
            if (itemClose == std::string::npos) {
                break;
            }
            std::string block = xml.substr(itemOpen, itemClose - itemOpen);
            std::string text;
            std::size_t cursor = 0;
            while (true) {
                std::size_t textOpen = block.find("<t", cursor);
                if (textOpen == std::string::npos) {
                    break;
                }
                std::size_t gt = block.find('>', textOpen);
                std::size_t textClose = gt == std::string::npos ? std::string::npos : block.find("</t>", gt);
                if (gt == std::string::npos || textClose == std::string::npos) {
                    break;
                }
                text += block.substr(gt + 1, textClose - gt - 1);
                cursor = textClose + 4;
            }
            result.push_back(DecodeXml(text));
            position = itemClose + 5;
        }
        return result;
    }

    void LoadSheets() {
        std::string workbookXml = ReadEntry("xl/workbook.xml");
        std::string relsXml = ReadEntry("xl/_rels/workbook.xml.rels");
        std::unordered_map<std::string, std::string> relationshipTargets;
        std::size_t position = 0;
        while (true) {
            std::size_t open = relsXml.find("<Relationship ", position);
            if (open == std::string::npos) {
                break;
            }
            std::size_t close = relsXml.find(">", open);
            if (close == std::string::npos) {
                break;
            }
            std::string tag = relsXml.substr(open, close - open);
            relationshipTargets[XmlAttr(tag, "Id")] = XmlAttr(tag, "Target");
            position = close + 1;
        }

        position = 0;
        while (true) {
            std::size_t open = workbookXml.find("<sheet ", position);
            if (open == std::string::npos) {
                break;
            }
            std::size_t close = workbookXml.find('>', open);
            if (close == std::string::npos) {
                break;
            }
            std::string tag = workbookXml.substr(open, close - open);
            std::string name = DecodeXml(XmlAttr(tag, "name"));
            std::string relationshipId = XmlAttr(tag, "r:id");
            if (relationshipId.empty()) {
                relationshipId = XmlAttr(tag, "id");
            }
            std::string target;
            auto iterator = relationshipTargets.find(relationshipId);
            if (iterator != relationshipTargets.end()) {
                target = iterator->second;
            }
            if (target.empty()) {
                target = "worksheets/sheet" + std::to_string(m_sheetNames.size() + 1) + ".xml";
            }
            if (!target.empty() && target[0] == '/') {
                target = target.substr(1);
            }
            while (target.rfind("../", 0) == 0) {
                target = target.substr(3);
            }
            ReplaceAll(target, "xl/../", "");
            if (target.rfind("xl/", 0) != 0) {
                target = "xl/" + target;
            }
            m_sheetNames.push_back(name);
            m_sheetPaths[name] = target;
            position = close + 1;
        }
    }

    Rows ParseSheet(const std::string& xml) const {
        Rows rows;
        std::size_t position = 0;
        while (true) {
            std::size_t rowOpen = xml.find("<row ", position);
            if (rowOpen == std::string::npos) {
                rowOpen = xml.find("<row>", position);
            }
            if (rowOpen == std::string::npos) {
                break;
            }
            std::size_t rowClose = xml.find("</row>", rowOpen);
            if (rowClose == std::string::npos) {
                break;
            }
            std::string rowBlock = xml.substr(rowOpen, rowClose - rowOpen);
            int rowNumber = std::atoi(XmlAttr(rowBlock, "r").c_str());
            if (rowNumber <= 0) {
                rowNumber = static_cast<int>(rows.size()) + 1;
            }
            if (static_cast<std::size_t>(rowNumber) > rows.size()) {
                rows.resize(static_cast<std::size_t>(rowNumber));
            }
            Row& row = rows[static_cast<std::size_t>(rowNumber) - 1];

            std::size_t cursor = 0;
            while (true) {
                std::size_t cellOpen = rowBlock.find("<c ", cursor);
                if (cellOpen == std::string::npos) {
                    cellOpen = rowBlock.find("<c>", cursor);
                }
                if (cellOpen == std::string::npos) {
                    break;
                }
                std::size_t gt = rowBlock.find('>', cellOpen);
                if (gt == std::string::npos) {
                    break;
                }
                bool selfClosing = gt > 0 && rowBlock[gt - 1] == '/';
                std::size_t cellEnd = gt + 1;
                if (!selfClosing) {
                    std::size_t closeTag = rowBlock.find("</c>", cellOpen);
                    if (closeTag == std::string::npos) {
                        break;
                    }
                    cellEnd = closeTag + 4;
                }
                std::string cellBlock = rowBlock.substr(cellOpen, cellEnd - cellOpen);
                std::string reference = XmlAttr(cellBlock, "r");
                std::string type = XmlAttr(cellBlock, "t");
                int columnIndex = ColumnIndex(reference);
                if (columnIndex < 0) {
                    columnIndex = static_cast<int>(row.size());
                }
                std::string text;
                if (type == "inlineStr") {
                    text = XmlElementText(cellBlock, "t");
                } else {
                    std::string raw = XmlElementText(cellBlock, "v");
                    if (type == "s") {
                        int sharedIndex = std::atoi(raw.c_str());
                        if (sharedIndex >= 0 && static_cast<std::size_t>(sharedIndex) < m_sharedStrings.size()) {
                            text = m_sharedStrings[static_cast<std::size_t>(sharedIndex)];
                        }
                    } else if (type == "b") {
                        text = raw == "1" ? "true" : "false";
                    } else {
                        text = raw;
                    }
                }
                if (static_cast<std::size_t>(columnIndex) >= row.size()) {
                    row.resize(static_cast<std::size_t>(columnIndex) + 1);
                }
                row[static_cast<std::size_t>(columnIndex)] = DecodeXml(text);
                cursor = cellEnd;
            }
            position = rowClose + 6;
        }
        return rows;
    }

    mz_zip_archive m_archive{};
    bool m_open = false;
    std::vector<std::string> m_sharedStrings;
    std::vector<std::string> m_sheetNames;
    std::unordered_map<std::string, std::string> m_sheetPaths;
};

}  // namespace cfg