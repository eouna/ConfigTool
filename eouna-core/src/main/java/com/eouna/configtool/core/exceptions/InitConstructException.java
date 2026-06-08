package com.eouna.configtool.core.exceptions;
/**
 * 初始化类构造函数时发生的异常
 *
 * @author CCL
 * @date 2023/6/19
 */
public class InitConstructException extends Exception {

  private final Class<?> errClass;
  private final String errorMsg;

  public InitConstructException(Class<?> aClass, String msg) {
    this.errClass = aClass;
    this.errorMsg = msg;
  }

  public InitConstructException(Class<?> aClass, String msg, Throwable exception) {
    super(exception);
    this.errClass = aClass;
    this.errorMsg = msg;
  }

  @Override
  public String getMessage() {
    return "msg: "
        + errorMsg
        + " occurred at class: "
        + errClass.getPackage().getName()
        + errClass.getSimpleName()
        + super.getMessage();
  }
}
