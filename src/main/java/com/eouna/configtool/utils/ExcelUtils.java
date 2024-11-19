package com.eouna.configtool.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

/**
 * excel工具集
 *
 * @author CCL
 * @date 2023/3/3
 */
public class ExcelUtils {

  /**
   * 将工作单元中都转为字符串进行处理
   *
   * @param cell 工作单元
   * @return 字符串
   */
  public static String getCellValue(Cell cell) {
    String value;
    switch (cell.getCellType()) {
        // 字符串
      case STRING:
        value = cell.getStringCellValue();
        break;
        // Boolean
      case BOOLEAN:
        value = cell.getBooleanCellValue() + "";
        break;
        // 公式
      case NUMERIC:
        if (String.valueOf(cell.getNumericCellValue()).contains("E")) {
          DataFormatter dataFormatter = new DataFormatter();
          return dataFormatter.formatCellValue(cell);
        }
        value = cell.getNumericCellValue() + "";
        break;
        // 空值
      case BLANK:
      default:
        value = "";
        break;
    }
    return value;
  }

  /** 检测空行 */
  public static boolean isBlankRow(Row row) {
    if (row == null) {
      return true;
    }
    for (Cell cell : row) {
      if (cell != null && cell.getCellType() != CellType.BLANK) {
        return false;
      }
    }
    return true;
  }

  static Pattern correctExcelFieldNamePattern = Pattern.compile("^[a-zA-Z]+(([0-9a-zA-Z])|_)*");
  /** 是否是错误的excel字段名 */
  public static boolean isIncorrectExcelFieldName(String fieldName) {
    // 如果是数字开头的配置表字段则不满足条件
    Matcher checker = correctExcelFieldNamePattern.matcher(fieldName.trim());
    return !checker.matches();
  }
}
