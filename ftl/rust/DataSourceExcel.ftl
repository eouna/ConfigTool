//! 配置数据源(excel实现, 自动生成)

use std::path::Path;

use calamine::{Data, Range, Reader};

use super::base_cfg_container::SheetData;

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

/// 创建数据源(excel实现)
pub fn create_data_source() -> Box<dyn DataSource> {
    Box::new(ExcelDataSource)
}

struct ExcelDataSource;

impl DataSource for ExcelDataSource {
    fn read_sheet(&self, request: &SheetReadRequest<'_>) -> Result<SheetData, String> {
        let path = Path::new(request.root_path).join(request.file_name);
        let mut workbook = calamine::open_workbook_auto(&path)
            .map_err(|error| format!("打开excel失败 {}: {}", path.display(), error))?;
        let sheet_name = workbook
            .sheet_names()
            .get(0)
            .cloned()
            .ok_or_else(|| format!("excel中没有工作薄: {}", path.display()))?;
        let range = workbook
            .worksheet_range(&sheet_name)
            .map_err(|error| format!("读取工作薄失败 {}: {}", sheet_name, error))?;
        Ok(range_to_sheet(&range))
    }

    fn read_constant_rows(
        &self,
        request: &SheetReadRequest<'_>,
    ) -> Result<Option<SheetData>, String> {
        if request.constant_sheet_name.is_empty() {
            return Ok(None);
        }
        let path = Path::new(request.root_path).join(request.file_name);
        let mut workbook = calamine::open_workbook_auto(&path)
            .map_err(|error| format!("打开excel失败 {}: {}", path.display(), error))?;
        match workbook.worksheet_range(request.constant_sheet_name) {
            Ok(range) => Ok(Some(range_to_sheet(&range))),
            Err(_) => Ok(None),
        }
    }

    fn is_single_source(&self) -> bool {
        false
    }
}

fn range_to_sheet(range: &Range<Data>) -> SheetData {
    let mut rows = Vec::with_capacity(range.height() as usize);
    for row in 0..range.height() {
        let mut values = Vec::with_capacity(range.width() as usize);
        for col in 0..range.width() {
            values.push(range_cell_text(range, row as usize, col as usize));
        }
        rows.push(values);
    }
    SheetData { rows }
}

fn range_cell_text(range: &Range<Data>, row: usize, col: usize) -> String {
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
