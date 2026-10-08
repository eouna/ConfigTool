//! ${containerClassName} 配置表容器(自动生成)
#![allow(non_snake_case)]

use std::collections::HashMap;

use calamine::Reader;

use crate::bean::${beanModule}::*;
use crate::container::CfgValue;

use super::base_cfg_container::{cell_text, find_col, is_blank_row, CfgBean};

/// @excelName ${excelName}
/// @sheetName ${sheetBean.sheetName}
/// @date ${date}
pub struct ${containerClassName} {
    /// key: 配置ID, value: 配置数据
    pub cfg_map: HashMap<i32, ${beanClassName}>,
<#list constantFields as constField>
    /// ${constField.fieldDesc.fieldData}
    pub ${constField.fieldName.fieldData}: ${constField.fieldType.fieldData},
</#list>
}

impl Default for ${containerClassName} {
    fn default() -> Self {
        Self::new()
    }
}

impl ${containerClassName} {
    /// 创建容器
    pub fn new() -> Self {
        Self {
            cfg_map: HashMap::new(),
<#list constantFields as constField>
            ${constField.fieldName.fieldData}: Default::default(),
</#list>
        }
    }

    /// 绑定的excel文件列表
    pub fn excel_names() -> Vec<&'static str> {
        vec![
<#list bindExcelList as excelName>
            "${excelName}",
</#list>
        ]
    }

    /// 加载配置表数据
    pub fn load(&mut self, root: &str) -> Result<(), String> {
        let mut temp_map: HashMap<i32, ${beanClassName}> = HashMap::new();
        for file_name in Self::excel_names() {
            let path = std::path::Path::new(root).join(file_name);
            let mut workbook = calamine::open_workbook_auto(&path)
                .map_err(|e| format!("打开excel失败 {}: {}", path.display(), e))?;
            let sheet_name = workbook
                .sheet_names()
                .get(0)
                .cloned()
                .ok_or_else(|| format!("excel中没有工作薄: {}", path.display()))?;
            let range = workbook
                .worksheet_range(&sheet_name)
                .map_err(|e| format!("读取工作薄失败 {}: {}", sheet_name, e))?;
            let names: Vec<String> = (0..range.width())
                .map(|col| cell_text(&range, ${fieldInfo.fieldName.configBindRow}, col as usize))
                .collect();
            let types: Vec<String> = (0..range.width())
                .map(|col| cell_text(&range, ${fieldInfo.fieldType.configBindRow}, col as usize))
                .collect();
            for row in ${dataStartRow}..(range.height() as usize) {
                if is_blank_row(&range, row) {
                    continue;
                }
                let mut bean = ${beanClassName}::default();
                if let Some(index) = find_col(&names, "${idName}") {
                    bean.${idName} = <i32 as CfgValue>::parse(&types[index], &cell_text(&range, row, index))?;
                }
<#list dataStruct.excelFieldInfoList as excelFieldInfo>
                if let Some(index) = find_col(&names, "${excelFieldInfo.fieldName.fieldData}") {
                    bean.${excelFieldInfo.fieldName.fieldData} = <${excelFieldInfo.fieldType.fieldData} as CfgValue>::parse(&types[index], &cell_text(&range, row, index))?;
                }
</#list>
                let id = bean.get_id();
                if temp_map.contains_key(&id) {
                    return Err(format!("出现重复的ID: {}", id));
                }
                temp_map.insert(id, bean);
            }
<#if (constantFields?size > 0)>
            if let Ok(const_range) = workbook.worksheet_range("${constantSheetName}") {
                for row in 1..(const_range.height() as usize) {
                    let name = cell_text(&const_range, row, ${constFieldInfo.fieldName.configBindRow});
                    if name.is_empty() {
                        continue;
                    }
                    let raw_type = cell_text(&const_range, row, ${constFieldInfo.fieldType.configBindRow});
                    let value_text = cell_text(&const_range, row, ${constFieldInfo.fieldVal.configBindRow});
                    match name.as_str() {
<#list constantFields as constField>
                        "${constField.fieldName.fieldData}" => {
                            self.${constField.fieldName.fieldData} = <${constField.fieldType.fieldData} as CfgValue>::parse(&raw_type, &value_text)?;
                        }
</#list>
                        _ => {}
                    }
                }
            }
</#if>
        }
        self.cfg_map = temp_map;
        Ok(())
    }
}