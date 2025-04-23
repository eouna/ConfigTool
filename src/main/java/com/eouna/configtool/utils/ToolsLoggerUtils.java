package com.eouna.configtool.utils;

import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.ui.controllers.ExcelGenWindowController;
import com.eouna.configtool.ui.controllers.ShowModalController;
import javafx.application.Platform;
import javafx.scene.text.TextFlow;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.controlsfx.dialog.ExceptionDialog;

/**
 * @author CCL
 */
public class ToolsLoggerUtils {

  private static final int MAX_EXCEPTION_DIALOG_SHOW_LEN = 1000;

  /**
   * 展示错误弹窗
   *
   * @param title 标题
   * @param e 异常
   */
  public static void showErrorDialog(String title, Throwable e) {
    String content =
        "[   msg   ]: " + e.getMessage() + "\n[   trace  ]: " + ExceptionUtils.getStackTrace(e);
    TextAreaLogger textAreaLogger = getMainTextAreaLog();
    if (textAreaLogger != null) {
      textAreaLogger.error(title + ": " + content);
    }
    Platform.runLater(
        () -> {
          if (e.getMessage().length() < MAX_EXCEPTION_DIALOG_SHOW_LEN) {
            ExceptionDialog exceptionDialog = new ExceptionDialog(e);
            exceptionDialog.setTitle(title);
            exceptionDialog.setHeaderText("异常信息");
            exceptionDialog.showAndWait();
          } else {
            ShowModalController show =
                WindowManager.getInstance().openWindow(ShowModalController.class, title);
            show.appendText(content);
          }
        });
  }

  /**
   * 展示错误弹窗
   *
   * @param title 标题
   * @param content 错误信息
   */
  public static void showErrorDialog(String title, String content) {
    TextAreaLogger textAreaLogger = getMainTextAreaLog();
    if (textAreaLogger != null) {
      textAreaLogger.error(title + ": " + content);
    }
    Platform.runLater(
        () -> {
          if (content.length() < MAX_EXCEPTION_DIALOG_SHOW_LEN) {
            ExceptionDialog exceptionDialog = new ExceptionDialog(new RuntimeException(content));
            exceptionDialog.setTitle(title);
            exceptionDialog.setHeaderText("异常信息");
            exceptionDialog.showAndWait();
          } else {
            ShowModalController show =
                WindowManager.getInstance().openWindow(ShowModalController.class, title);
            show.appendText(content);
          }
        });
  }

  /**
   * 获取主窗口日志文本域
   *
   * @return 主窗口日志文本域
   */
  public static TextAreaLogger getMainTextAreaLog() {
    if (WindowManager.getInstance().isWindowInitialized(ExcelGenWindowController.class)) {
      ExcelGenWindowController mainWindowController =
          WindowManager.getInstance().getController(ExcelGenWindowController.class);
      return mainWindowController.getTextAreaLogger();
    }
    return null;
  }
}
