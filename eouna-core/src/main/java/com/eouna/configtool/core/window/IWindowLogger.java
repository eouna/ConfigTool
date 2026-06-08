package com.eouna.configtool.core.window;

/**
 * 窗口日志打印接口
 *
 * @author CCL
 */
public interface IWindowLogger {
  /**
   * 普通日志
   *
   * @param msg 日志信息
   * @param arrays 参数
   */
  void info(String msg, Object... arrays);

  /**
   * 成功类的日志
   *
   * @param msg 日志信息
   * @param arrays 参数
   */
  void success(String msg, Object... arrays);

  /**
   * debug日志
   *
   * @param debugMsg 日志信息
   * @param arrays 参数
   */
  void debug(String debugMsg, Object... arrays);

  /**
   * 警告日志
   *
   * @param warnMsg 日志信息
   * @param arrays 参数
   */
  void warn(String warnMsg, Object... arrays);

  /**
   * 异常日志
   *
   * @param errorMsg 日志信息
   * @param arrays 参数
   */
  void error(String errorMsg, Object... arrays);

  /**
   * 异常日志
   *
   * @param errorMsg 日志信息
   * @param e 异常类
   * @param arrays 参数
   */
  void error(String errorMsg, Exception e, Object... arrays);
}
