package com.eouna.configtool.generator.exceptions;

import com.eouna.configtool.generator.bean.ExcelDataStruct;
import com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelFieldInfo;
import com.eouna.configtool.utils.ExcelUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

/**
 * excel解析异常
 *
 * @author CCL
 * @date 2023/3/3
 */
public class ExcelParseException extends BaseExcelException {

  public ExcelParseException() {}

  public ExcelParseException(String msg) {
    super(msg);
  }

  String excelFileName;
  ExcelFieldInfo excelFieldInfo;
  Row row;
  Cell cell;

  public ExcelParseException(String file, String message) {
    super(message);
    this.excelFileName = file;
  }

  public ExcelParseException(String file, Throwable e) {
    super(e);
    this.excelFileName = file;
  }

  public ExcelParseException(ExcelFieldInfo excelFieldInfo, Row row, Cell cell, Throwable e) {
    super(e);
    this.excelFileName = row.getSheet().getSheetName();
    this.excelFieldInfo = excelFieldInfo;
    this.row = row;
    this.cell = cell;
  }

  public ExcelParseException(
      ExcelDataStruct.ExcelFieldInfo excelFieldInfo, Row row, Cell cell, String message) {
    super(message);
    this.excelFieldInfo = excelFieldInfo;
    this.row = row;
    this.cell = cell;
  }

  @Override
  public String getMessage() {
    if (row != null && cell != null && excelFieldInfo != null) {
      return "解析文件: "
          + row.getSheet().getSheetName()
          + " 行: "
          + row.getRowNum()
          + " 列: "
          + cell.getColumnIndex()
          + " 字段名: "
          + excelFieldInfo.getFieldName().getFieldData()
          + " 字段类型: "
          + excelFieldInfo.getFieldType().getFieldData()
          + " 数据: "
          + ExcelUtils.getCellValue(cell)
          + " 异常: "
          + super.getMessage();
    } else {
      return "解析文件: " + excelFileName + " 时发生异常:" + super.getMessage();
    }
  }
}
