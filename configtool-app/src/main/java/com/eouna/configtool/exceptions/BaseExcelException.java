package com.eouna.configtool.exceptions;

/**
 * 基础配置表异常
 *
 * @author CCL
 * @date 2023/3/8
 */
public class BaseExcelException extends RuntimeException {

  protected StringBuilder appendMsg = new StringBuilder();

  public BaseExcelException() {}

  public BaseExcelException(String message) {
    super(message);
  }

  public BaseExcelException(String message, Throwable cause) {
    super(message, cause);
  }

  public BaseExcelException(Throwable cause) {
    super(cause);
  }

  public BaseExcelException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public void appendExceptionStr(String exceptionStr) {
    appendMsg.append(exceptionStr);
  }

  @Override
  public String getMessage() {
    return appendMsg.toString() + "#" + super.getMessage();
  }
}
