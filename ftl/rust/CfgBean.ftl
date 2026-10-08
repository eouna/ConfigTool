//! ${beanClassName} 配置bean(自动生成)
//!
//! 说明: Rust 不支持把 enum 声明为 struct 的关联项(impl中只允许fn/const/type),
//! 因此枚举与其cfg放在同一个模块(同一个文件)内, 效果等同于"每个cfg自带自己的枚举"。
#![allow(non_snake_case)]

use crate::container::CfgBean;

/// @excelName ${dataStruct.fileName}
/// @sheetName ${dataStruct.sheetName}
/// @date ${date}
#[derive(Debug, Clone, Default)]
pub struct ${beanClassName} {
    /// ${idName}
    pub ${idName}: i32,
<#list dataStruct.excelFieldInfoList as excelFieldInfo>
    /// ${excelFieldInfo.fieldDesc.fieldData}
    pub ${excelFieldInfo.fieldName.fieldData}: ${excelFieldInfo.fieldType.fieldData},
</#list>
}

impl CfgBean for ${beanClassName} {
    fn get_id(&self) -> i32 {
        self.${idName}
    }
}
<#list enumDefs as enumDef>

/// ${enumDef.name} 枚举(与 ${beanClassName} 同模块内嵌)
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum ${enumDef.name} {
<#list enumDef.values as value>
    ${value?cap_first},
</#list>
}

impl Default for ${enumDef.name} {
    fn default() -> Self {
<#if (enumDef.values?size > 0)>
        ${enumDef.name}::${enumDef.values?first?cap_first}
<#else>
        unreachable!("枚举${enumDef.name}没有配置任何值")
</#if>
    }
}

impl ${enumDef.name} {
    /// 枚举对应的配置字符串
    pub fn as_str(&self) -> &'static str {
        match self {
<#list enumDef.values as value>
            ${enumDef.name}::${value?cap_first} => "${value}",
</#list>
        }
    }

    /// 由配置字符串获取枚举
    pub fn from_str(value: &str) -> Option<Self> {
        match value {
<#list enumDef.values as value>
            "${value}" => Some(${enumDef.name}::${value?cap_first}),
</#list>
            _ => None,
        }
    }
}

impl crate::container::CfgValue for ${enumDef.name} {
    fn parse(_raw_type: &str, text: &str) -> Result<Self, String> {
        Self::from_str(text.trim()).ok_or_else(|| format!("未知的${enumDef.name}枚举值: {}", text))
    }
}
</#list>