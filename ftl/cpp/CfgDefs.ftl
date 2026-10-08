#pragma once

#include <algorithm>
#include <cctype>
#include <charconv>
#include <cstdint>
#include <ctime>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

namespace cfg {

using Row = std::vector<std::string>;
using Rows = std::vector<Row>;

/// 去除首尾空白
inline std::string Trim(const std::string& value) {
    std::size_t begin = 0;
    while (begin < value.size() && std::isspace(static_cast<unsigned char>(value[begin])) != 0) {
        ++begin;
    }
    std::size_t end = value.size();
    while (end > begin && std::isspace(static_cast<unsigned char>(value[end - 1])) != 0) {
        --end;
    }
    return value.substr(begin, end - begin);
}

/// 按单字符分隔
inline std::vector<std::string> Split(const std::string& value, char delimiter) {
    std::vector<std::string> result;
    std::string current;
    for (char ch : value) {
        if (ch == delimiter) {
            result.push_back(current);
            current.clear();
        } else {
            current.push_back(ch);
        }
    }
    result.push_back(current);
    return result;
}

/// 去掉浮点数小数部分(兼容配置里的x.x取整)
inline std::string IntPart(const std::string& value) {
    std::size_t dot = value.find('.');
    return dot != std::string::npos && dot > 0 ? value.substr(0, dot) : value;
}

inline void ReplaceAll(std::string& value, const std::string& from, const std::string& to) {
    if (from.empty()) {
        return;
    }
    std::size_t position = 0;
    while ((position = value.find(from, position)) != std::string::npos) {
        value.replace(position, from.size(), to);
        position += to.size();
    }
}

inline std::string Join(const std::vector<std::string>& values, const std::string& separator) {
    std::string result;
    for (std::size_t i = 0; i < values.size(); ++i) {
        if (i > 0) {
            result += separator;
        }
        result += values[i];
    }
    return result;
}

inline std::string JoinPath(const std::string& root, const std::string& name) {
    if (root.empty()) {
        return name;
    }
    char last = root[root.size() - 1];
    if (last == '/' || last == '\\') {
        return root + name;
    }
    return root + "/" + name;
}

/// 取数据行
inline const Row& RowAt(const Rows& rows, int rowIndex) {
    static const Row kEmpty;
    if (rowIndex < 0 || static_cast<std::size_t>(rowIndex) >= rows.size()) {
        return kEmpty;
    }
    return rows[static_cast<std::size_t>(rowIndex)];
}

/// 取单元格文本
inline std::string CellAt(const Row& row, std::size_t colIndex) {
    return colIndex < row.size() ? Trim(row[colIndex]) : std::string();
}

/// 取类型描述
inline std::string TypeAt(const Row& typeRow, std::size_t colIndex) {
    return colIndex < typeRow.size() ? Trim(typeRow[colIndex]) : std::string();
}

/// 按字段名查找列下标
inline bool FindColumn(const std::vector<std::string>& names, const std::string& fieldName, std::size_t& columnIndex) {
    for (std::size_t i = 0; i < names.size(); ++i) {
        if (names[i] == fieldName) {
            columnIndex = i;
            return true;
        }
    }
    return false;
}

/// 列表/集合类型描述: list<int>{,10}
struct SeqSpec {
    std::string subType;
    char delimiter = ',';
    int limit = 0;
};

inline SeqSpec ParseSeqSpec(const std::string& rawType) {
    SeqSpec spec;
    std::size_t open = rawType.find('<');
    std::size_t brace = rawType.rfind('{');
    if (open == std::string::npos || brace == std::string::npos || brace <= open + 1) {
        throw std::runtime_error("列表类型描述错误: " + rawType);
    }
    spec.subType = Trim(rawType.substr(open + 1, brace - 1 - (open + 1)));
    std::string inner = rawType.substr(brace + 1);
    if (!inner.empty()) {
        spec.delimiter = inner[0];
    }
    std::string digits;
    for (char ch : inner) {
        if (std::isdigit(static_cast<unsigned char>(ch)) != 0) {
            digits.push_back(ch);
        }
    }
    if (!digits.empty()) {
        spec.limit = std::stoi(digits);
    }
    return spec;
}

/// 键值对类型描述: map<int{,}string>{;}
struct MapSpec {
    std::string keyType;
    char keyDelimiter = ',';
    std::string valueType;
    char entryDelimiter = ';';
    int limit = 0;
};

inline MapSpec ParseMapSpec(const std::string& rawType) {
    MapSpec spec;
    std::size_t open = rawType.find('<');
    std::size_t brace = rawType.rfind('{');
    if (open == std::string::npos || brace == std::string::npos || brace <= open + 1) {
        throw std::runtime_error("map类型描述错误: " + rawType);
    }
    std::string inner = rawType.substr(brace + 1);
    if (!inner.empty()) {
        spec.entryDelimiter = inner[0];
    }
    std::string digits;
    for (char ch : inner) {
        if (std::isdigit(static_cast<unsigned char>(ch)) != 0) {
            digits.push_back(ch);
        }
    }
    if (!digits.empty()) {
        spec.limit = std::stoi(digits);
    }
    std::string body = rawType.substr(open + 1, brace - 1 - (open + 1));
    std::size_t keyBrace = body.find('{');
    std::size_t keyClose = keyBrace == std::string::npos ? std::string::npos : body.find('}', keyBrace);
    if (keyBrace == std::string::npos || keyClose == std::string::npos) {
        throw std::runtime_error("map key类型描述错误: " + rawType);
    }
    spec.keyType = Trim(body.substr(0, keyBrace));
    std::string keyDelimiters = body.substr(keyBrace + 1, keyClose - keyBrace - 1);
    if (!keyDelimiters.empty()) {
        spec.keyDelimiter = keyDelimiters[0];
    }
    spec.valueType = Trim(body.substr(keyClose + 1));
    return spec;
}

/// 配置表时间
struct CfgDateTime {
    int year = 1970;
    int month = 1;
    int day = 1;
    int hour = 0;
    int minute = 0;
    int second = 0;

    std::string ToString() const {
        char buffer[32] = {0};
        std::snprintf(buffer, sizeof(buffer), "%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second);
        return std::string(buffer);
    }

    /// 按 date<yyyy-MM-dd HH:mm> 里的格式解析
    static CfgDateTime Parse(const std::string& javaFormat, const std::string& text) {
        std::string format = javaFormat;
        ReplaceAll(format, "yyyy", "%Y");
        ReplaceAll(format, "MM", "%m");
        ReplaceAll(format, "dd", "%d");
        ReplaceAll(format, "HH", "%H");
        ReplaceAll(format, "mm", "%M");
        ReplaceAll(format, "ss", "%S");
        std::tm tm{};
        tm.tm_year = 70;
        tm.tm_mon = 0;
        tm.tm_mday = 1;
        std::istringstream stream(text);
        stream >> std::get_time(&tm, format.c_str());
        if (stream.fail()) {
            throw std::runtime_error("时间格式: " + javaFormat + " 和数据源: " + text + " 不匹配");
        }
        CfgDateTime value;
        value.year = tm.tm_year + 1900;
        value.month = tm.tm_mon + 1;
        value.day = tm.tm_mday;
        value.hour = tm.tm_hour;
        value.minute = tm.tm_min;
        value.second = tm.tm_sec;
        return value;
    }
};

/// 取 date<...> 中的格式串
inline std::string DateFormatOf(const std::string& rawType) {
    std::size_t open = rawType.find('<');
    std::size_t close = rawType.rfind('>');
    if (open == std::string::npos || close == std::string::npos || close <= open) {
        return "%Y-%m-%d %H:%M";
    }
    return rawType.substr(open + 1, close - open - 1);
}

/// 字段解析: 按excel类型描述把文本解析为目标类型(与Java模板的字段适配器等价)
template <typename T>
struct CfgValue {
    static T Parse(const std::string& rawType, const std::string& text) {
        (void)rawType;
        (void)text;
        throw std::runtime_error("不支持的字段类型");
    }
};

template <>
struct CfgValue<int8_t> {
    static int8_t Parse(const std::string&, const std::string& text) {
        return static_cast<int8_t>(std::stoll(IntPart(Trim(text))));
    }
};

template <>
struct CfgValue<int16_t> {
    static int16_t Parse(const std::string&, const std::string& text) {
        return static_cast<int16_t>(std::stoll(IntPart(Trim(text))));
    }
};

template <>
struct CfgValue<int32_t> {
    static int32_t Parse(const std::string&, const std::string& text) {
        return static_cast<int32_t>(std::stoll(IntPart(Trim(text))));
    }
};

template <>
struct CfgValue<int64_t> {
    static int64_t Parse(const std::string&, const std::string& text) { return std::stoll(IntPart(Trim(text))); }
};

template <>
struct CfgValue<float> {
    static float Parse(const std::string&, const std::string& text) { return std::stof(Trim(text)); }
};

template <>
struct CfgValue<double> {
    static double Parse(const std::string&, const std::string& text) { return std::stod(Trim(text)); }
};

template <>
struct CfgValue<bool> {
    static bool Parse(const std::string&, const std::string& text) {
        std::string value = Trim(text);
        std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
            return static_cast<char>(std::tolower(ch));
        });
        if (value == "true" || value == "1") {
            return true;
        }
        if (value == "false" || value == "0") {
            return false;
        }
        throw std::runtime_error("布尔值解析失败: " + text);
    }
};

template <>
struct CfgValue<std::string> {
    static std::string Parse(const std::string&, const std::string& text) { return text; }
};

template <>
struct CfgValue<CfgDateTime> {
    static CfgDateTime Parse(const std::string& rawType, const std::string& text) {
        return CfgDateTime::Parse(DateFormatOf(rawType), Trim(text));
    }
};

template <typename T>
struct CfgValue<std::vector<T>> {
    static std::vector<T> Parse(const std::string& rawType, const std::string& text) {
        SeqSpec spec = ParseSeqSpec(rawType);
        std::vector<std::string> parts = Split(text, spec.delimiter);
        if (spec.limit > 0 && static_cast<int>(parts.size()) > spec.limit) {
            throw std::runtime_error("字段对应的数据数量: " + std::to_string(parts.size()) + " 超过限制值: "
                                     + std::to_string(spec.limit));
        }
        std::vector<T> result;
        for (const std::string& part : parts) {
            std::string item = Trim(part);
            if (item.empty()) {
                continue;
            }
            result.push_back(CfgValue<T>::Parse(spec.subType, item));
        }
        return result;
    }
};

template <typename K, typename V>
struct CfgValue<std::unordered_map<K, V>> {
    static std::unordered_map<K, V> Parse(const std::string& rawType, const std::string& text) {
        MapSpec spec = ParseMapSpec(rawType);
        std::vector<std::string> entries = Split(text, spec.entryDelimiter);
        if (spec.limit > 0 && static_cast<int>(entries.size()) > spec.limit) {
            throw std::runtime_error("字段对应的数据数量: " + std::to_string(entries.size()) + " 超过限制值: "
                                     + std::to_string(spec.limit));
        }
        std::unordered_map<K, V> result;
        for (const std::string& entry : entries) {
            std::string item = Trim(entry);
            if (item.empty()) {
                continue;
            }
            std::size_t position = item.find(spec.keyDelimiter);
            if (position == std::string::npos) {
                throw std::runtime_error("map数据格式错误: " + item);
            }
            K key = CfgValue<K>::Parse(spec.keyType, Trim(item.substr(0, position)));
            V value = CfgValue<V>::Parse(spec.valueType, Trim(item.substr(position + 1)));
            result[key] = value;
        }
        return result;
    }
};

/// 调试输出
inline std::string ToStr(const std::string& value) { return value; }

inline std::string ToStr(bool value) { return value ? "true" : "false"; }

inline std::string ToStr(int8_t value) { return std::to_string(static_cast<int>(value)); }

inline std::string ToStr(int16_t value) { return std::to_string(static_cast<int>(value)); }

inline std::string ToStr(int32_t value) { return std::to_string(value); }

inline std::string ToStr(int64_t value) { return std::to_string(value); }

inline std::string ToStr(float value) {
    // 最短往返表示(与其它语言模板输出一致)
    char buffer[64] = {0};
    std::to_chars_result result = std::to_chars(buffer, buffer + sizeof(buffer), value);
    return std::string(buffer, result.ptr);
}

inline std::string ToStr(double value) {
    char buffer[64] = {0};
    std::to_chars_result result = std::to_chars(buffer, buffer + sizeof(buffer), value);
    return std::string(buffer, result.ptr);
}

inline std::string ToStr(const CfgDateTime& value) { return value.ToString(); }

template <typename T>
std::string ToStr(const std::vector<T>& values) {
    std::vector<std::string> parts;
    parts.reserve(values.size());
    for (const T& item : values) {
        parts.push_back(ToStr(item));
    }
    return "[" + Join(parts, ", ") + "]";
}

template <typename K, typename V>
std::string ToStr(const std::unordered_map<K, V>& values) {
    std::vector<std::string> parts;
    parts.reserve(values.size());
    for (const auto& pair : values) {
        parts.push_back(ToStr(pair.first) + ":" + ToStr(pair.second));
    }
    return "{" + Join(parts, ", ") + "}";
}

}  // namespace cfg