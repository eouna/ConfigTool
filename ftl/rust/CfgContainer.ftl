//! ${containerClassName} 配置表容器(自动生成)
#![allow(non_snake_case)]

use std::collections::HashMap;

use crate::container::{create_data_source, CfgValue, FieldMeta, SheetReadRequest};
use crate::bean::${beanModule}::*;

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
    /// 当前容器对应的表名(json数据源按 <表名>.json 读取)
    const SHEET_NAME: &'static str = "${sheetName}";

    /// 字段元数据(字段名 + 配置表中的原始类型)
    fn field_metas() -> Vec<FieldMeta> {
        vec![
<#list fieldMetas as meta>
            FieldMeta { name: "${meta.name}".to_string(), raw_type: "${meta.rawType}".to_string() },
</#list>
        ]
    }

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
        let data_source = create_data_source();
        let field_metas = Self::field_metas();
        let source_names: Vec<String> = if data_source.is_single_source() {
            vec![format!("{}.xlsx", Self::SHEET_NAME)]
        } else {
            Self::excel_names()
                .into_iter()
                .map(|name| name.to_string())
                .collect()
        };
        let mut temp_map: HashMap<i32, ${beanClassName}> = HashMap::new();
        for file_name in source_names {
            let request = SheetReadRequest {
                root_path: root,
                file_name: &file_name,
                sheet_name: Self::SHEET_NAME,
                fields: &field_metas,
                name_row: ${fieldInfo.fieldName.configBindRow},
                type_row: ${fieldInfo.fieldType.configBindRow},
                data_start_row: ${dataStartRow},
                constant_sheet_name: "${constantSheetName}",
            };
            let sheet_data = data_source.read_sheet(&request)?;
            let names: Vec<String> = (0..sheet_data.width())
                .map(|col| cell_text(&sheet_data, ${fieldInfo.fieldName.configBindRow}, col))
                .collect();
            let types: Vec<String> = (0..sheet_data.width())
                .map(|col| cell_text(&sheet_data, ${fieldInfo.fieldType.configBindRow}, col))
                .collect();
            for row in ${dataStartRow}..sheet_data.height() {
                if is_blank_row(&sheet_data, row) {
                    continue;
                }
                let mut bean = ${beanClassName}::default();
                if let Some(index) = find_col(&names, "${idName}") {
                    bean.${idName} = <i32 as CfgValue>::parse(
                        &types[index],
                        &cell_text(&sheet_data, row, index),
                    )?;
                }
<#list dataStruct.excelFieldInfoList as excelFieldInfo>
                if let Some(index) = find_col(&names, "${excelFieldInfo.fieldName.fieldData}") {
                    bean.${excelFieldInfo.fieldName.fieldData} =
                        <${excelFieldInfo.fieldType.fieldData} as CfgValue>::parse(
                            &types[index],
                            &cell_text(&sheet_data, row, index),
                        )?;
                }
</#list>
                let id = bean.get_id();
                if temp_map.contains_key(&id) {
                    return Err(format!("出现重复的ID: {}", id));
                }
                temp_map.insert(id, bean);
            }
<#if (constantFields?size > 0)>
            if let Some(const_data) = data_source.read_constant_rows(&request)? {
                for row in 1..const_data.height() {
                    let name = cell_text(&const_data, row, ${constFieldInfo.fieldName.configBindRow});
                    if name.is_empty() {
                        continue;
                    }
                    let raw_type = cell_text(&const_data, row, ${constFieldInfo.fieldType.configBindRow});
                    let value_text = cell_text(&const_data, row, ${constFieldInfo.fieldVal.configBindRow});
                    match name.as_str() {
<#list constantFields as constField>
                        "${constField.fieldName.fieldData}" => {
                            self.${constField.fieldName.fieldData} =
                                <${constField.fieldType.fieldData} as CfgValue>::parse(&raw_type, &value_text)?;
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
