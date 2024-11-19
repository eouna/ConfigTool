package com.eouna.configtool.exceptions;

import com.eouna.configtool.utils.StrUtils;

/**
 * excel解析异常
 *
 * @author CCL
 * @date 2023/3/3
 */
public class ExcelDataParseException extends BaseExcelException {
  final String excelFileName;

  public ExcelDataParseException(String file, String message) {
    super(message);
    this.excelFileName = file;
  }

  public ExcelDataParseException(String file, Throwable e) {
    super(e);
    this.excelFileName = file;
  }

  @Override
  public String getMessage() {
    if (!StrUtils.isEmpty(excelFileName)) {
      return "解析文件: " + excelFileName + " 时发生异常:" + super.getMessage();
    } else {
      return "发生异常:" + super.getMessage();
    }
  }
}
