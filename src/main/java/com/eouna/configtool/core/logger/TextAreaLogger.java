package com.eouna.configtool.core.logger;

import com.eouna.configtool.core.window.IWindowLogger;
import com.eouna.configtool.utils.LoggerUtils;
import com.eouna.configtool.utils.LoggerUtils.LogLevel;
import javafx.scene.text.TextFlow;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * 日志文本框logger
 *
 * @author CCL
 * @date 2023/4/6
 */
public class TextAreaLogger implements IWindowLogger {

  /** 日志显示区域 */
  protected TextFlow loggerShowAreaUndertaker;

  public TextAreaLogger() {}

  public TextAreaLogger(TextFlow specifyLoggerArea) {
    this.loggerShowAreaUndertaker = specifyLoggerArea;
  }

  public TextFlow getLoggerShowAreaUndertaker() {
    return loggerShowAreaUndertaker;
  }

  @Override
  public void info(String msg, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(loggerShowAreaUndertaker, LogLevel.INFO, msg, arrays);
  }

  @Override
  public void success(String msg, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(loggerShowAreaUndertaker, LogLevel.SUCCESS, msg, arrays);
  }

  @Override
  public void debug(String debugMsg, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(loggerShowAreaUndertaker, LogLevel.DEBUG, debugMsg);
  }

  @Override
  public void warn(String warnMsg, Object... arrays) {
    LoggerUtils.getInstance().appendLogToTextarea(loggerShowAreaUndertaker, LogLevel.WARN, warnMsg);
  }

  @Override
  public void error(String errorMsg, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(loggerShowAreaUndertaker, LogLevel.ERROR, errorMsg, arrays);
  }

  @Override
  public void error(String errorMsg, Exception e, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(
            loggerShowAreaUndertaker,
            LogLevel.ERROR,
            errorMsg + " exception: \n" + ExceptionUtils.getStackTrace(e),
            arrays);
  }
}
