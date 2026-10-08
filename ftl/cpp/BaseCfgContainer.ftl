#pragma once

#include <cstddef>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <unordered_map>

namespace cfg {

/// 配置容器基类(与Java模板的BaseCfgContainer对应)
template <typename T>
class BaseCfgContainer {
 public:
    using CfgMap = std::unordered_map<int32_t, T>;

    virtual ~BaseCfgContainer() = default;

    /// 加载配置表(运行期读取xlsx并解析)
    virtual void Load(const std::string& resourceRootPath) = 0;

    /// 根据ID获取配置
    const T* Get(int32_t id) const {
        auto iterator = m_cfgMap.find(id);
        return iterator == m_cfgMap.end() ? nullptr : &iterator->second;
    }

    /// 全部配置
    const CfgMap& GetAll() const { return m_cfgMap; }

    /// 配置数量
    std::size_t Size() const { return m_cfgMap.size(); }

 protected:
    /// 字段描述行
    int m_fieldDescRow = ${fieldInfo.fieldDesc.configBindRow};
    /// 字段类型行
    int m_fieldTypeRow = ${fieldInfo.fieldType.configBindRow};
    /// 字段名行
    int m_fieldNameRow = ${fieldInfo.fieldName.configBindRow};
    /// 字段数值范围行
    int m_fieldDataRangeRow = ${fieldInfo.fieldDataRange.configBindRow};
    /// 数据读取开始行
    int m_dataStartRow = ${dataStartRow};
    /// 常量字段名列
    int m_constFieldNameRow = ${constFieldInfo.fieldName.configBindRow};
    /// 常量字段类型列
    int m_constFieldTypeRow = ${constFieldInfo.fieldType.configBindRow};
    /// 常量字段值列
    int m_constFieldDataVal = ${constFieldInfo.fieldVal.configBindRow};
    /// 常量配置工作薄名
    std::string m_constantSheetName = "${constantSheetName}";
    /// 数据范围列需要跳过的标记
    std::string m_skipStr = "${skipStr}";

    CfgMap m_cfgMap;
};

}  // namespace cfg