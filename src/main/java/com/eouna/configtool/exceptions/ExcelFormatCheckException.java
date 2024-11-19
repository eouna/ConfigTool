package com.eouna.configtool.exceptions;

/**
 * 配置表格式检查异常
 *
 * @author CCL
 * @date 2023/3/8
 */
public class ExcelFormatCheckException extends BaseExcelException {

  public ExcelFormatCheckException() {}

  public ExcelFormatCheckException(String message) {
    super(message);
  }

  public ExcelFormatCheckException(String message, Throwable cause) {
    super(message, cause);
  }
}
