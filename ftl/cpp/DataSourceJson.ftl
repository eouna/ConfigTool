#pragma once

#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <fstream>
#include <map>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#include "CfgDefs.h"

namespace cfg {

/// 字段元数据(字段名 + 配置表中的原始类型)
struct FieldMeta {
    std::string name;
    std::string rawType;
};

/// 读取一张表的请求参数
struct SheetReadRequest {
    std::string rootPath;
    std::string fileName;
    std::string sheetName;
    std::vector<FieldMeta> fields;
    int nameRow = 0;
    int typeRow = 0;
    int dataStartRow = 0;
    std::string constantSheetName;
};

namespace detail {

enum class JsonType { Null, Bool, Number, String, Array, Object };

struct JsonValue {
    JsonType type = JsonType::Null;
    bool boolValue = false;
    std::string text;
    std::vector<JsonValue> array;
    std::map<std::string, JsonValue> object;
};

inline void AppendCodePoint(std::string& out, std::uint32_t code) {
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

class JsonParser {
 public:
    explicit JsonParser(const std::string& text) : m_text(text) {}

    JsonValue Parse() {
        SkipWhitespace();
        JsonValue value = ParseValue();
        SkipWhitespace();
        if (m_position != m_text.size()) {
            Fail("根节点之后还有多余内容");
        }
        return value;
    }

 private:
    const std::string& m_text;
    std::size_t m_position = 0;

    JsonValue ParseValue() {
        if (m_position >= m_text.size()) {
            Fail("数据提前结束");
        }
        char ch = m_text[m_position];
        if (ch == '"') {
            JsonValue value;
            value.type = JsonType::String;
            value.text = ParseString();
            return value;
        }
        if (ch == '{') {
            return ParseObject();
        }
        if (ch == '[') {
            return ParseArray();
        }
        if (ch == 't') {
            Expect("true");
            JsonValue value;
            value.type = JsonType::Bool;
            value.boolValue = true;
            return value;
        }
        if (ch == 'f') {
            Expect("false");
            JsonValue value;
            value.type = JsonType::Bool;
            value.boolValue = false;
            return value;
        }
        if (ch == 'n') {
            Expect("null");
            return JsonValue{};
        }
        if (ch == '-' || std::isdigit(static_cast<unsigned char>(ch)) != 0) {
            return ParseNumber();
        }
        Fail(std::string("非法字符: ") + ch);
    }

    JsonValue ParseObject() {
        JsonValue value;
        value.type = JsonType::Object;
        ++m_position;
        SkipWhitespace();
        if (Consume('}')) {
            return value;
        }
        while (true) {
            SkipWhitespace();
            if (m_position >= m_text.size() || m_text[m_position] != '"') {
                Fail("对象key必须是字符串");
            }
            std::string key = ParseString();
            SkipWhitespace();
            if (!Consume(':')) {
                Fail("对象key之后缺少冒号");
            }
            SkipWhitespace();
            value.object[key] = ParseValue();
            SkipWhitespace();
            if (Consume('}')) {
                return value;
            }
            if (!Consume(',')) {
                Fail("对象条目之后缺少逗号");
            }
        }
    }

    JsonValue ParseArray() {
        JsonValue value;
        value.type = JsonType::Array;
        ++m_position;
        SkipWhitespace();
        if (Consume(']')) {
            return value;
        }
        while (true) {
            SkipWhitespace();
            value.array.push_back(ParseValue());
            SkipWhitespace();
            if (Consume(']')) {
                return value;
            }
            if (!Consume(',')) {
                Fail("数组元素之后缺少逗号");
            }
        }
    }

    JsonValue ParseNumber() {
        std::size_t begin = m_position;
        if (Peek() == '-') {
            ++m_position;
        }
        while (m_position < m_text.size() && std::isdigit(static_cast<unsigned char>(m_text[m_position])) != 0) {
            ++m_position;
        }
        if (m_position < m_text.size() && m_text[m_position] == '.') {
            ++m_position;
            while (m_position < m_text.size() && std::isdigit(static_cast<unsigned char>(m_text[m_position])) != 0) {
                ++m_position;
            }
        }
        if (m_position < m_text.size() && (m_text[m_position] == 'e' || m_text[m_position] == 'E')) {
            ++m_position;
            if (m_position < m_text.size() && (m_text[m_position] == '+' || m_text[m_position] == '-')) {
                ++m_position;
            }
            while (m_position < m_text.size() && std::isdigit(static_cast<unsigned char>(m_text[m_position])) != 0) {
                ++m_position;
            }
        }
        JsonValue value;
        value.type = JsonType::Number;
        value.text = m_text.substr(begin, m_position - begin);
        return value;
    }

    std::string ParseString() {
        if (!Consume('"')) {
            Fail("字符串缺少起始引号");
        }
        std::string result;
        while (m_position < m_text.size()) {
            char ch = m_text[m_position++];
            if (ch == '"') {
                return result;
            }
            if (ch != '\\') {
                result.push_back(ch);
                continue;
            }
            if (m_position >= m_text.size()) {
                Fail("字符串转义不完整");
            }
            char escaped = m_text[m_position++];
            switch (escaped) {
                case '"': result.push_back('"'); break;
                case '\\': result.push_back('\\'); break;
                case '/': result.push_back('/'); break;
                case 'b': result.push_back('\b'); break;
                case 'f': result.push_back('\f'); break;
                case 'n': result.push_back('\n'); break;
                case 'r': result.push_back('\r'); break;
                case 't': result.push_back('\t'); break;
                case 'u': {
                    std::uint32_t code = ParseHex4();
                    if (code >= 0xD800 && code <= 0xDBFF
                        && m_position + 2 <= m_text.size()
                        && m_text[m_position] == '\\'
                        && m_text[m_position + 1] == 'u') {
                        m_position += 2;
                        std::uint32_t low = ParseHex4();
                        if (low >= 0xDC00 && low <= 0xDFFF) {
                            code = 0x10000 + ((code - 0xD800) << 10) + (low - 0xDC00);
                        }
                    }
                    AppendCodePoint(result, code);
                    break;
                }
                default: Fail("未知的字符串转义");
            }
        }
        Fail("字符串缺少结束引号");
    }

    std::uint32_t ParseHex4() {
        std::uint32_t value = 0;
        for (int i = 0; i < 4; ++i) {
            if (m_position >= m_text.size()) {
                Fail("Unicode转义不完整");
            }
            char ch = m_text[m_position++];
            value <<= 4;
            if (ch >= '0' && ch <= '9') {
                value += static_cast<std::uint32_t>(ch - '0');
            } else if (ch >= 'a' && ch <= 'f') {
                value += static_cast<std::uint32_t>(ch - 'a' + 10);
            } else if (ch >= 'A' && ch <= 'F') {
                value += static_cast<std::uint32_t>(ch - 'A' + 10);
            } else {
                Fail("Unicode转义包含非法字符");
            }
        }
        return value;
    }

    void Expect(const std::string& token) {
        if (m_text.compare(m_position, token.size(), token) != 0) {
            Fail("缺少字面量: " + token);
        }
        m_position += token.size();
    }

    void SkipWhitespace() {
        while (m_position < m_text.size()
               && std::isspace(static_cast<unsigned char>(m_text[m_position])) != 0) {
            ++m_position;
        }
    }

    bool Consume(char expected) {
        if (m_position < m_text.size() && m_text[m_position] == expected) {
            ++m_position;
            return true;
        }
        return false;
    }

    char Peek() const {
        return m_position < m_text.size() ? m_text[m_position] : '\0';
    }

    [[noreturn]] void Fail(const std::string& message) const {
        throw std::runtime_error("json解析失败(位置 " + std::to_string(m_position) + "): " + message);
    }
};

}  // namespace detail

/// 配置数据源(json实现)
class DataSource {
 public:
    /// 读取 <表名>.json 并还原成与excel一致的二维字符串表
    Rows ReadSheet(const SheetReadRequest& request) const {
        const std::string path = JoinPath(request.rootPath, request.sheetName + ".json");
        const std::string content = ReadFile(path);
        detail::JsonValue root = detail::JsonParser(content).Parse();
        if (root.type != detail::JsonType::Object) {
            throw std::runtime_error("json根节点不是对象: " + path);
        }

        const std::size_t fieldCount = request.fields.size();
        const std::size_t headRowCount = static_cast<std::size_t>(
            std::max(request.dataStartRow, std::max(request.nameRow, request.typeRow) + 1));
        Rows rows(headRowCount, Row(fieldCount));
        for (std::size_t row = 0; row < headRowCount; ++row) {
            for (std::size_t col = 0; col < fieldCount; ++col) {
                rows[row][col] = row == static_cast<std::size_t>(request.typeRow)
                                     ? request.fields[col].rawType
                                     : request.fields[col].name;
            }
        }

        std::vector<std::pair<std::string, const detail::JsonValue*>> entries;
        for (const auto& item : root.object) {
            if (Trim(item.first) != "0" && item.second.type == detail::JsonType::Object) {
                entries.emplace_back(item.first, &item.second);
            }
        }
        std::sort(entries.begin(), entries.end(), [](const auto& left, const auto& right) {
            return CompareId(left.first, right.first);
        });

        std::size_t dataRow = static_cast<std::size_t>(request.dataStartRow);
        for (const auto& entry : entries) {
            const detail::JsonValue& item = *entry.second;
            if (dataRow >= rows.size()) {
                rows.emplace_back(fieldCount, std::string());
            }
            bool hasValue = false;
            for (std::size_t col = 0; col < fieldCount; ++col) {
                auto iterator = item.object.find(request.fields[col].name);
                if (iterator == item.object.end() || iterator->second.type == detail::JsonType::Null) {
                    continue;
                }
                rows[dataRow][col] = ValueToConfigString(request.fields[col].rawType, iterator->second);
                hasValue = true;
            }
            if (hasValue) {
                ++dataRow;
            }
        }
        return rows;
    }

    /// 读取常量表(<常量表名>.json, 由工具按excel行列布局导出)
    ///
    /// 常量表在excel中单独维护, 导出json时会一并导出为 <常量表名>.json, 因此json数据源同样能读到常量;
    /// 文件不存在时返回false(视为没有常量表), 常量字段保持默认值。
    bool ReadConstantRows(const SheetReadRequest& request, Rows& rows) const {
        if (request.constantSheetName.empty()) {
            return false;
        }
        const std::string path = JoinPath(request.rootPath, request.constantSheetName + ".json");
        std::ifstream probe(path, std::ios::binary);
        if (!probe) {
            return false;
        }
        probe.close();
        const detail::JsonValue root = detail::JsonParser(ReadFile(path)).Parse();
        if (root.type != detail::JsonType::Array) {
            throw std::runtime_error("常量表json根节点不是数组: " + path);
        }
        Rows constantRows;
        constantRows.reserve(root.array.size());
        for (const detail::JsonValue& rowValue : root.array) {
            if (rowValue.type != detail::JsonType::Array) {
                continue;
            }
            Row row;
            row.reserve(rowValue.array.size());
            for (const detail::JsonValue& cell : rowValue.array) {
                row.push_back(ScalarToString(cell));
            }
            constantRows.push_back(std::move(row));
        }
        rows = std::move(constantRows);
        return true;
    }

    /// json数据一张表对应一个文件
    bool SingleSource() const { return true; }

 private:
    static std::string ReadFile(const std::string& path) {
        std::ifstream input(path, std::ios::binary);
        if (!input) {
            throw std::runtime_error("找不到json数据文件: " + path);
        }
        std::ostringstream buffer;
        buffer << input.rdbuf();
        return buffer.str();
    }

    static std::string ValueToConfigString(const std::string& rawType, const detail::JsonValue& value) {
        const std::string raw = Trim(rawType);
        std::string lower = raw;
        std::transform(lower.begin(), lower.end(), lower.begin(), [](unsigned char ch) {
            return static_cast<char>(std::tolower(ch));
        });
        if (lower.rfind("date<", 0) == 0) {
            return value.type == detail::JsonType::String ? NormalizeDateValue(value.text) : ScalarToString(value);
        }
        if (lower.rfind("list<", 0) == 0 || lower.rfind("set<", 0) == 0) {
            SeqSpec spec = ParseSeqSpec(raw);
            return JoinValues(spec.subType, spec.delimiter, value);
        }
        if (lower.rfind("map<", 0) == 0) {
            MapSpec spec = ParseMapSpec(raw);
            return JoinMapValues(spec, value);
        }
        return ScalarToString(value);
    }

    static std::string JoinValues(const std::string& subType, char delimiter, const detail::JsonValue& value) {
        if (value.type != detail::JsonType::Array) {
            return ScalarToString(value);
        }
        std::vector<std::string> parts;
        parts.reserve(value.array.size());
        for (const detail::JsonValue& item : value.array) {
            parts.push_back(ValueToConfigString(subType, item));
        }
        return Join(parts, std::string(1, delimiter));
    }

    static std::string JoinMapValues(const MapSpec& spec, const detail::JsonValue& value) {
        if (value.type != detail::JsonType::Object) {
            return ScalarToString(value);
        }
        std::vector<std::pair<std::string, const detail::JsonValue*>> entries;
        for (const auto& item : value.object) {
            entries.emplace_back(item.first, &item.second);
        }
        std::sort(entries.begin(), entries.end(), [](const auto& left, const auto& right) {
            return CompareId(left.first, right.first);
        });
        std::vector<std::string> parts;
        parts.reserve(entries.size());
        for (const auto& entry : entries) {
            parts.push_back(entry.first + std::string(1, spec.keyDelimiter)
                            + ValueToConfigString(spec.valueType, *entry.second));
        }
        return Join(parts, std::string(1, spec.entryDelimiter));
    }

    static std::string ScalarToString(const detail::JsonValue& value) {
        switch (value.type) {
            case detail::JsonType::String:
            case detail::JsonType::Number:
                return value.text;
            case detail::JsonType::Bool:
                return value.boolValue ? "true" : "false";
            default:
                return std::string();
        }
    }

    static std::string NormalizeDateValue(const std::string& text) {
        std::string value = Trim(text);
        if (value.size() >= 10 && value[4] == '-' && value[7] == '-') {
            std::string date = value.substr(0, 10);
            if (value.size() >= 19 && (value[10] == 'T' || value[10] == ' ')) {
                return date + " " + value.substr(11, 8);
            }
            return date + " 00:00:00";
        }
        return value;
    }

    static bool CompareId(const std::string& left, const std::string& right) {
        char* leftEnd = nullptr;
        char* rightEnd = nullptr;
        long long leftNumber = std::strtoll(left.c_str(), &leftEnd, 10);
        long long rightNumber = std::strtoll(right.c_str(), &rightEnd, 10);
        if (leftEnd != left.c_str() && rightEnd != right.c_str() && *leftEnd == '\0' && *rightEnd == '\0') {
            return leftNumber < rightNumber;
        }
        return left < right;
    }
};

}  // namespace cfg
