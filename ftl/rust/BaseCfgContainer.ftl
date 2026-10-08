//! 配置表容器基础运行时(自动生成)
#![allow(dead_code)]

use std::collections::HashMap;
use std::hash::Hash;

use calamine::{Data, Range};
use chrono::NaiveDateTime;

/// 配置Bean接口
pub trait CfgBean {
    fn get_id(&self) -> i32;
}

/// 配置字段解析接口(按excel类型描述递归解析)
pub trait CfgValue: Sized {
    fn parse(raw_type: &str, text: &str) -> Result<Self, String>;
}

/// 读取单元格文本
pub fn cell_text(range: &Range<Data>, row: usize, col: usize) -> String {
    match range.get_value((row as u32, col as u32)) {
        Some(Data::String(value)) => value.trim().to_string(),
        Some(Data::Int(value)) => value.to_string(),
        Some(Data::Float(value)) => {
            if value.fract() == 0.0 {
                format!("{}", *value as i64)
            } else {
                format!("{}", value)
            }
        }
        Some(Data::Bool(value)) => value.to_string(),
        Some(Data::DateTime(value)) => value.to_string(),
        Some(Data::DateTimeIso(value)) => value.clone(),
        Some(Data::DurationIso(value)) => value.clone(),
        _ => String::new(),
    }
}

/// 按字段名查找列下标
pub fn find_col(names: &[String], name: &str) -> Option<usize> {
    names.iter().position(|item| item == name)
}

/// 是否空行
pub fn is_blank_row(range: &Range<Data>, row: usize) -> bool {
    (0..range.width()).all(|col| cell_text(range, row, col as usize).is_empty())
}

/// 去掉浮点数小数部分
fn int_like(text: &str) -> String {
    match text.find('.') {
        Some(index) if index > 0 => text[..index].to_string(),
        _ => text.to_string(),
    }
}

/// Java时间格式转chrono格式
fn java_date_to_chrono(java_format: &str) -> String {
    java_format
        .replace("yyyy", "%Y")
        .replace("MM", "%m")
        .replace("dd", "%d")
        .replace("HH", "%H")
        .replace("mm", "%M")
        .replace("ss", "%S")
}

/// 解析 date<格式>
fn date_format(raw_type: &str) -> Option<String> {
    let start = raw_type.find('<')?;
    let end = raw_type.rfind('>')?;
    Some(raw_type[start + 1..end].to_string())
}

/// 解析 list<...>{分隔符长度} / set<...>{分隔符长度}
fn seq_spec(raw_type: &str) -> Option<(String, char, usize)> {
    let lower = raw_type.trim().to_lowercase();
    if !(lower.starts_with("list<") || lower.starts_with("set<")) {
        return None;
    }
    let start = raw_type.find('<')?;
    let brace = raw_type.rfind('{')?;
    let sub_type = raw_type[start + 1..brace - 1].trim().to_string();
    let inner = &raw_type[brace + 1..];
    let delimiter = inner.chars().next()?;
    let limit_text: String = inner[1..].chars().filter(|c| c.is_ascii_digit()).collect();
    let limit = limit_text.parse::<usize>().unwrap_or(0);
    Some((sub_type, delimiter, limit))
}

/// 解析 map<键类型{分隔符}值类型>{分隔符长度}
fn map_spec(raw_type: &str) -> Option<(String, char, String, char, usize)> {
    let lower = raw_type.trim().to_lowercase();
    if !lower.starts_with("map<") {
        return None;
    }
    let start = raw_type.find('<')?;
    let brace = raw_type.rfind('{')?;
    let inner = &raw_type[brace + 1..];
    let entry_delimiter = inner.chars().next()?;
    let limit_text: String = inner[1..].chars().filter(|c| c.is_ascii_digit()).collect();
    let limit = limit_text.parse::<usize>().unwrap_or(0);
    let body = &raw_type[start + 1..brace - 1];
    let key_brace = body.find('{')?;
    let key_close = key_brace + body[key_brace..].find('}')?;
    let key_type = body[..key_brace].trim().to_string();
    let key_delimiter = body[key_brace + 1..key_close].chars().next()?;
    let value_type = body[key_close + 1..].trim().to_string();
    Some((key_type, key_delimiter, value_type, entry_delimiter, limit))
}

macro_rules! impl_int_value {
    ($($t:ty),*) => {
        $(
            impl CfgValue for $t {
                fn parse(_raw_type: &str, text: &str) -> Result<Self, String> {
                    let cleaned = int_like(text);
                    cleaned
                        .trim()
                        .parse::<$t>()
                        .map_err(|e| format!("整数解析失败: {} ({})", text, e))
                }
            }
        )*
    };
}

macro_rules! impl_float_value {
    ($($t:ty),*) => {
        $(
            impl CfgValue for $t {
                fn parse(_raw_type: &str, text: &str) -> Result<Self, String> {
                    text.trim()
                        .parse::<$t>()
                        .map_err(|e| format!("浮点数解析失败: {} ({})", text, e))
                }
            }
        )*
    };
}

impl_int_value!(i8, i16, i32, i64, u8, u16, u32, u64);
impl_float_value!(f32, f64);

impl CfgValue for bool {
    fn parse(_raw_type: &str, text: &str) -> Result<Self, String> {
        text.trim()
            .parse::<bool>()
            .map_err(|e| format!("布尔值解析失败: {} ({})", text, e))
    }
}

impl CfgValue for String {
    fn parse(_raw_type: &str, text: &str) -> Result<Self, String> {
        Ok(text.to_string())
    }
}

/// 配置表时间类型
#[derive(Debug, Clone, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct CfgDateTime(pub NaiveDateTime);

impl Default for CfgDateTime {
    fn default() -> Self {
        CfgDateTime(default_datetime())
    }
}

impl CfgValue for CfgDateTime {
    fn parse(raw_type: &str, text: &str) -> Result<Self, String> {
        let format =
            date_format(raw_type).ok_or_else(|| format!("时间类型描述错误: {}", raw_type))?;
        NaiveDateTime::parse_from_str(text.trim(), &java_date_to_chrono(&format))
            .map(CfgDateTime)
            .map_err(|e| format!("时间格式: {} 和数据源: {} 不匹配 ({})", format, text, e))
    }
}

/// 默认时间(1970-01-01 00:00)
pub fn default_datetime() -> NaiveDateTime {
    NaiveDateTime::parse_from_str("1970-01-01 00:00", "%Y-%m-%d %H:%M")
        .expect("默认时间解析失败")
}

impl<T: CfgValue> CfgValue for Vec<T> {
    fn parse(raw_type: &str, text: &str) -> Result<Self, String> {
        let (sub_type, delimiter, limit) =
            seq_spec(raw_type).ok_or_else(|| format!("列表类型描述错误: {}", raw_type))?;
        let is_set = raw_type.trim().to_lowercase().starts_with("set<");
        let parts: Vec<&str> = text.split(delimiter).collect();
        if limit > 0 && parts.len() > limit {
            return Err(format!(
                "字段对应的数据数量: {} 超过限制值: {}",
                parts.len(),
                limit
            ));
        }
        let mut seen: std::collections::HashSet<String> = std::collections::HashSet::new();
        let mut result = Vec::new();
        for part in parts {
            let part = part.trim();
            if part.is_empty() {
                continue;
            }
            if is_set && !seen.insert(part.to_string()) {
                return Err(format!("Set列数据出现重复数据: {}", part));
            }
            result.push(T::parse(&sub_type, part)?);
        }
        Ok(result)
    }
}

impl<K: CfgValue + Eq + Hash, V: CfgValue> CfgValue for HashMap<K, V> {
    fn parse(raw_type: &str, text: &str) -> Result<Self, String> {
        let (key_type, key_delimiter, value_type, entry_delimiter, limit) =
            map_spec(raw_type).ok_or_else(|| format!("map类型描述错误: {}", raw_type))?;
        let entries: Vec<&str> = text.split(entry_delimiter).collect();
        if limit > 0 && entries.len() > limit {
            return Err(format!(
                "字段对应的数据数量: {} 超过限制值: {}",
                entries.len(),
                limit
            ));
        }
        let mut result = HashMap::new();
        for entry in entries {
            let entry = entry.trim();
            if entry.is_empty() {
                continue;
            }
            let pair: Vec<&str> = entry.splitn(2, key_delimiter).collect();
            if pair.len() < 2 {
                return Err(format!("map数据格式错误: {}", entry));
            }
            let key = K::parse(&key_type, pair[0].trim())?;
            let value = V::parse(&value_type, pair[1].trim())?;
            result.insert(key, value);
        }
        Ok(result)
    }
}