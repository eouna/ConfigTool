package com.eouna.configtool.utils.logger;

import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaStepLogger;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.ui.controllers.ExcelGenWindowController;
import javafx.scene.text.TextFlow;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * 主窗口步数日志
 *
 * @author CCL
 */
public class MainWindowStepLogger extends TextAreaStepLogger {

  /** 当日志显示区域刷新后的回调 */
  protected Runnable afterFreshCallback =
      () -> {
        if (WindowManager.getInstance().isWindowInitialized(ExcelGenWindowController.class)) {
          ExcelGenWindowController mainWindowController =
              WindowManager.getInstance().getController(ExcelGenWindowController.class);
          // 重新计算滚动条高度
          mainWindowController.getLogShowScrollPane().setVvalue(1D);
        }
      };

  protected TextFlow loggerShowAreaUndertaker;

  public MainWindowStepLogger(TextFlow specifyLoggerArea) {
    super(specifyLoggerArea);
  }

  @Override
  public void info(String msg, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(
            loggerShowAreaUndertaker,
            LoggerUtils.LogLevel.INFO,
            decorateMsg(msg),
            afterFreshCallback,
            arrays);
  }

  @Override
  public void success(String msg, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(
            loggerShowAreaUndertaker,
            LoggerUtils.LogLevel.SUCCESS,
            decorateMsg(msg),
            afterFreshCallback,
            arrays);
  }

  @Override
  public void debug(String debugMsg, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(
            loggerShowAreaUndertaker,
            LoggerUtils.LogLevel.DEBUG,
            decorateMsg(debugMsg),
            afterFreshCallback);
  }

  @Override
  public void warn(String warnMsg, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(
            loggerShowAreaUndertaker,
            LoggerUtils.LogLevel.WARN,
            decorateMsg(warnMsg),
            afterFreshCallback);
  }

  @Override
  public void error(String errorMsg, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(
            loggerShowAreaUndertaker,
            LoggerUtils.LogLevel.ERROR,
            decorateMsg(errorMsg),
            afterFreshCallback,
            arrays);
  }

  @Override
  public void error(String errorMsg, Exception e, Object... arrays) {
    LoggerUtils.getInstance()
        .appendLogToTextarea(
            loggerShowAreaUndertaker,
            LoggerUtils.LogLevel.ERROR,
            decorateMsg(errorMsg + " exception: \n" + ExceptionUtils.getStackTrace(e)),
            afterFreshCallback,
            arrays);
  }
}
