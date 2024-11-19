package com.eouna.configtool.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.eouna.configtool.core.logger.TextAreaStepLogger;
import com.eouna.configtool.core.window.IWindowLogger;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.ui.controllers.ExcelGenWindowController;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.ui.controllers.ShowModalController;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.controlsfx.dialog.ExceptionDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * 日志工具类
 *
 * @author CCL
 * @date 2023/3/1
 */
public final class LoggerUtils {

  private Logger logger;

  /** 窗口日志打印接口 */
  private IWindowLogger logShowLogger;

  private TextFlow textArea;
  /** 日志行数计数器 */
  private final Map<TextFlow, AtomicInteger> logAreaRowCounter = new ConcurrentHashMap<>();

  private static final int MAX_EXCEPTION_DIALOG_SHOW_LEN = 1000;

  public void init() {
    if (this.logger == null) {
      this.logger = LoggerFactory.getLogger(this.getClass());
    }
    logShowLogger = new TextAreaLogger();
  }

  public enum LogLevel {
    // 日志等级
    INFO,
    SUCCESS,
    DEBUG,
    WARN,
    ERROR
  }

  public static Logger getLogger() {
    return LoggerUtils.getInstance().logger;
  }

  public static IWindowLogger getTextareaLogger() {
    return LoggerUtils.getInstance().logShowLogger;
  }

  public static IWindowLogger getTextareaLogger(TextFlow logShowArea) {
    return new TextAreaLogger(logShowArea);
  }

  public static TextAreaStepLogger getStepTextareaLogger(TextFlow logShowArea) {
    return new TextAreaStepLogger(logShowArea);
  }

  /**
   * 展示错误弹窗
   *
   * @param title 标题
   * @param e 异常
   */
  public static void showErrorDialog(String title, Throwable e) {
    String content =
        "[   msg   ]: " + e.getMessage() + "\n[   trace  ]: " + ExceptionUtils.getStackTrace(e);
    LoggerUtils.getTextareaLogger().error(title + " :" + content);
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
    LoggerUtils.getTextareaLogger().error(title + " :" + content);
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

  public void initLogComponent(TextFlow textArea) {
    if (this.textArea == null) {
      this.textArea = textArea;
    }
  }

  public void removeLoggerCounter(TextFlow textFlow) {
    logAreaRowCounter.remove(textFlow);
  }

  /**
   * 将日志打印到窗口上
   *
   * @param logLevel 日志等级
   * @param logStr 日志信息
   */
  public void appendLogToTextarea(
      TextFlow textFlow, LogLevel logLevel, String logStr, Object... args) {
    // 由于需要处理UI相关的东西需要抛入UI线程进行处理
    Platform.runLater(() -> appendLogInFxThread(textFlow, logLevel, logStr, args));
  }

  /**
   * 通过fx线程将日志打印到窗口上
   *
   * @param logLevel 日志等级
   * @param originLogStr 日志信息
   */
  private void appendLogInFxThread(
      TextFlow textFlow, LogLevel logLevel, String originLogStr, Object... args) {
    try {
      Color color;
      String logStr = MessageFormatter.arrayFormat(originLogStr, args).getMessage();
      switch (logLevel) {
        case INFO:
          LoggerUtils.getLogger().info("msg: {}", logStr);
          color = Color.BLACK;
          break;
        case DEBUG:
          LoggerUtils.getLogger().debug("msg: {}", logStr);
          color = Color.WHEAT;
          break;
        case WARN:
          LoggerUtils.getLogger().warn("msg: {}", logStr);
          color = Color.YELLOW;
          break;
        case ERROR:
          LoggerUtils.getLogger().error("msg: {}", logStr);
          color = Color.RED;
          break;
        case SUCCESS:
          LoggerUtils.getLogger().info("msg: {}", logStr);
          color = Color.SPRINGGREEN;
          break;
        default:
          throw new RuntimeException("not found log level");
      }
      // 刷新日志区域
      refreshLogTextArea(textFlow, logLevel, logStr, color);
      if (WindowManager.getInstance().isWindowInitialized(ExcelGenWindowController.class)) {
        ExcelGenWindowController mainWindowController =
            WindowManager.getInstance().getController(ExcelGenWindowController.class);
        // 重新计算滚动条高度
        mainWindowController.getLogShowScrollPane().setVvalue(1D);
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("移除头部文件失败", e);
    }
  }

  /**
   * 刷新日志文件展示区域
   *
   * @param textFlow 日志区域
   * @param logLevel 日志等级
   * @param logStr 日志字符串
   * @param color 颜色
   */
  private void refreshLogTextArea(
      TextFlow textFlow, LogLevel logLevel, String logStr, Color color) {
    TextFlow logArea = textFlow;
    if (logArea == null) {
      logArea = textArea;
    }
    if (logArea != null) {
      String[] wrapStrArr = logStr.split("\n");
      for (int i = 0; i < wrapStrArr.length; i++) {
        AtomicInteger counter = logAreaRowCounter.get(logArea);
        if (counter == null) {
          logAreaRowCounter.put(logArea, new AtomicInteger());
        }
        if (logAreaRowCounter.get(logArea).incrementAndGet()
            > DefaultEnvConfigConstant.LOG_AREA_MAX_SHOW_NUM) {
          if (logArea.getChildren().size() > 0) {
            logArea.getChildren().remove(0);
          }
          logAreaRowCounter.get(logArea).decrementAndGet();
        }
      }
      Text text = new Text(buildLogData(logLevel, logStr) + "\n");
      text.setFont(new Font(13));
      text.setFill(color);
      logArea.getChildren().add(text);
    }
  }

  private String buildLogData(LogLevel logLevel, String logStr) {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss");
    String dateStr = simpleDateFormat.format(new Date());
    return "[" + logLevel.name() + "] [" + dateStr + "] [MSG]: " + logStr;
  }

  /**
   * 异常展示
   *
   * @param title 标题
   * @param msg 信息
   * @param throwable 异常
   */
  public static void showError(String title, String msg, Throwable throwable) {
    ExceptionDialog exceptionDialog = new ExceptionDialog(throwable);
    exceptionDialog.setTitle(title);
    exceptionDialog.setContentText(msg);
    exceptionDialog.showAndWait();
  }

  /**
   * 单例
   *
   * @return LoggerUtils
   */
  public static LoggerUtils getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final LoggerUtils instance;

    Singleton() {
      this.instance = new LoggerUtils();
    }

    public LoggerUtils getInstance() {
      return instance;
    }
  }
}
