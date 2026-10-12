//! 配置数据源(json实现, 自动生成)

use std::cmp::Ordering;
use std::fs;
use std::path::Path;

use chrono::{DateTime, NaiveDate, NaiveDateTime};
use serde_json::Value;

use super::base_cfg_container::{
    date_format, java_date_to_chrono, map_spec, seq_spec, SheetData,
};

/// 字段元数据(字段名 + 配置表中的原始类型)
#[derive(Debug, Clone)]
pub struct FieldMeta {
    pub name: String,
    pub raw_type: String,
}

/// 读取一张表的请求参数
#[derive(Debug, Clone)]
pub struct SheetReadRequest<'a> {
    pub root_path: &'a str,
    pub file_name: &'a str,
    pub sheet_name: &'a str,
    pub fields: &'a [FieldMeta],
    pub name_row: usize,
    pub type_row: usize,
    pub data_start_row: usize,
    pub constant_sheet_name: &'a str,
}

/// 数据源接口: 把配置读成统一的二维字符串表
pub trait DataSource {
    fn read_sheet(&self, request: &SheetReadRequest<'_>) -> Result<SheetData, String>;
    fn read_constant_rows(&self, request: &SheetReadRequest<'_>) -> Result<Option<SheetData>, String>;
    fn is_single_source(&self) -> bool;
}

/// 创建数据源(json实现)
pub fn create_data_source() -> Box<dyn DataSource> {
    Box::new(JsonDataSource)
}

struct JsonDataSource;

impl DataSource for JsonDataSource {
    fn read_sheet(&self, request: &SheetReadRequest<'_>) -> Result<SheetData, String> {
        let path = Path::new(request.root_path).join(format!("{}.json", request.sheet_name));
        let content = fs::read_to_string(&path)
            .map_err(|error| format!("读取json失败 {}: {}", path.display(), error))?;
        let root_value: Value = serde_json::from_str(&content)
            .map_err(|error| format!("解析json失败 {}: {}", path.display(), error))?;
        let object = root_value
            .as_object()
            .ok_or_else(|| format!("json根节点不是对象: {}", path.display()))?;

        let field_count = request.fields.len();
        let head_row_count = request
            .data_start_row
            .max(request.name_row.max(request.type_row) + 1);
        let mut rows = vec![vec![String::new(); field_count]; head_row_count];
        for row in 0..head_row_count {
            for (col, meta) in request.fields.iter().enumerate() {
                rows[row][col] = if row == request.type_row {
                    meta.raw_type.clone()
                } else {
                    meta.name.clone()
                };
            }
        }

        let mut entries: Vec<(&String, &Value)> = object
            .iter()
            .filter(|(key, value)| key.trim() != "0" && value.is_object())
            .collect();
        entries.sort_by(|(left, _), (right, _)| compare_id(left, right));

        let mut data_row = request.data_start_row;
        for (_, item) in entries {
            let item_object = item
                .as_object()
                .ok_or_else(|| format!("json配置行不是对象: {}", item))?;
            let mut has_value = false;
            for (col, meta) in request.fields.iter().enumerate() {
                let Some(value) = item_object.get(&meta.name) else {
                    continue;
                };
                if value.is_null() {
                    continue;
                }
                if data_row >= rows.len() {
                    rows.push(vec![String::new(); field_count]);
                }
                rows[data_row][col] = value_to_config_string(&meta.raw_type, value)?;
                has_value = true;
            }
            if has_value {
                data_row += 1;
            }
        }
        Ok(SheetData { rows })
    }

    fn read_constant_rows(
        &self,
        _request: &SheetReadRequest<'_>,
    ) -> Result<Option<SheetData>, String> {
        // json数据不包含常量表, 常量字段保持默认值
        Ok(None)
    }

    fn is_single_source(&self) -> bool {
        true
    }
}

fn value_to_config_string(raw_type: &str, value: &Value) -> Result<String, String> {
    let raw = raw_type.trim();
    let lower = raw.to_ascii_lowercase();
    if lower.starts_with("date<") {
        let format = date_format(raw).ok_or_else(|| format!("时间类型描述错误: {}", raw))?;
        let text = value
            .as_str()
            .ok_or_else(|| format!("时间字段不是字符串: {}", value))?;
        return Ok(format_date(&format, text));
    }
    if lower.starts_with("list<") || lower.starts_with("set<") {
        let (sub_type, delimiter, _) = seq_spec(raw)
            .ok_or_else(|| format!("列表类型描述错误: {}", raw))?;
        return join_values(&sub_type, delimiter, value);
    }
    if lower.starts_with("map<") {
        let (_, key_delimiter, value_type, entry_delimiter, _) =
            map_spec(raw).ok_or_else(|| format!("map类型描述错误: {}", raw))?;
        return join_map_values(&key_delimiter, &value_type, entry_delimiter, value);
    }
    Ok(scalar_to_string(value))
}

fn join_values(sub_type: &str, delimiter: char, value: &Value) -> Result<String, String> {
    let Some(items) = value.as_array() else {
        return Ok(scalar_to_string(value));
    };
    let mut parts = Vec::with_capacity(items.len());
    for item in items {
        parts.push(value_to_config_string(sub_type, item)?);
    }
    Ok(parts.join(&delimiter.to_string()))
}

fn join_map_values(
    key_delimiter: &char,
    value_type: &str,
    entry_delimiter: char,
    value: &Value,
) -> Result<String, String> {
    let Some(entries) = value.as_object() else {
        return Ok(scalar_to_string(value));
    };
    let mut sorted: Vec<(&String, &Value)> = entries.iter().collect();
    sorted.sort_by(|(left, _), (right, _)| compare_id(left, right));
    let mut parts = Vec::with_capacity(sorted.len());
    for (key, item) in sorted {
        parts.push(format!(
            "{}{}{}",
            key,
            key_delimiter,
            value_to_config_string(value_type, item)?
        ));
    }
    Ok(parts.join(&entry_delimiter.to_string()))
}

fn scalar_to_string(value: &Value) -> String {
    match value {
        Value::String(text) => text.clone(),
        Value::Bool(flag) => flag.to_string(),
        Value::Number(number) => number.to_string(),
        Value::Null => String::new(),
        _ => value.to_string(),
    }
}

fn format_date(format: &str, text: &str) -> String {
    let chrono_format = java_date_to_chrono(format);
    if let Ok(parsed) = DateTime::parse_from_rfc3339(text) {
        return parsed
            .naive_local()
            .format(&chrono_format)
            .to_string();
    }
    if let Ok(parsed) = DateTime::parse_from_str(text, "%Y-%m-%dT%H:%M:%S%z") {
        return parsed
            .naive_local()
            .format(&chrono_format)
            .to_string();
    }
    for pattern in ["%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M"] {
        if let Ok(parsed) = NaiveDateTime::parse_from_str(text, pattern) {
            return parsed.format(&chrono_format).to_string();
        }
    }
    if let Ok(parsed) = NaiveDate::parse_from_str(text, "%Y-%m-%d") {
        if let Some(value) = parsed.and_hms_opt(0, 0, 0) {
            return value.format(&chrono_format).to_string();
        }
    }
    text.to_string()
}

fn compare_id(left: &str, right: &str) -> Ordering {
    match (left.parse::<i64>(), right.parse::<i64>()) {
        (Ok(left_number), Ok(right_number)) => left_number.cmp(&right_number),
        _ => left.cmp(right),
    }
}
