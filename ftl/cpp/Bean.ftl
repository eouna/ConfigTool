#pragma once

#include <cstdint>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

#include "CfgDefs.h"

/// ${beanClassName} 配置结构(自动生成)
/// @excelName ${excelName}
/// @sheetName ${sheetName}
/// @date ${date}
struct ${beanClassName} {
<#list enumDefs as enumDef>
    /// ${enumDef.name} 枚举(内嵌在该cfg中)
    enum class ${enumDef.name} {
<#list enumDef.values as value>
        ${value?cap_first},
</#list>
    };

</#list>
    /// ${idName}
    int32_t ${idName} = 0;
<#list fields as field>
    /// ${field.desc}
    ${field.type} ${field.name}{};
</#list>

    /// 配置ID
    int32_t GetId() const { return ${idName}; }

    /// 调试输出
    std::string ToString() const {
        return std::string("${beanClassName}{") + "${idName}=" + cfg::ToStr(${idName})<#list fields as field> + ", ${field.name}=" + <#if field.isEnum>${field.enumName}ToString(${field.name})<#else>cfg::ToStr(${field.name})</#if></#list> + "}";
    }
<#list enumDefs as enumDef>

    /// ${enumDef.name} 转字符串
    static std::string ${enumDef.name}ToString(${enumDef.name} value) {
        switch (value) {
<#list enumDef.values as value>
            case ${enumDef.name}::${value?cap_first}:
                return "${value}";
</#list>
        }
        return std::string();
    }
</#list>
};
<#list enumDefs as enumDef>

/// ${enumDef.name} 的解析适配(与Java模板的枚举适配器等价, 特化必须位于cfg命名空间内)
namespace cfg {
template <>
struct CfgValue<${beanClassName}::${enumDef.name}> {
    static ${beanClassName}::${enumDef.name} Parse(const std::string&, const std::string& text) {
        std::string value = Trim(text);
<#list enumDef.values as value>
        if (value == "${value}") {
            return ${beanClassName}::${enumDef.name}::${value?cap_first};
        }
</#list>
        throw std::runtime_error("未知的枚举值: " + value);
    }
};
}  // namespace cfg
</#list>