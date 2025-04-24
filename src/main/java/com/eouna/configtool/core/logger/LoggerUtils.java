package com.eouna.configtool.core.logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.eouna.configtool.core.window.IWindowLogger;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.ui.controllers.ExcelGenWindowController;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.ui.controllers.ShowModalController;
import com.google.common.base.Supplier;
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

  /** 日志行数计数器 */
  private final Map<TextFlow, AtomicInteger> logAreaRowCounter = new ConcurrentHashMap<>();

  public void init() {
    if (this.logger == null) {
      this.logger = LoggerFactory.getLogger(this.getClass());
    }
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
    Platform.runLater(() -> appendLogInFxThread(textFlow, logLevel, logStr, null, args));
  }

  /**
   * 将日志打印到窗口上
   *
   * @param logLevel 日志等级
   * @param logStr 日志信息
   * @param afterRefreshCallback 日志区域刷新之后的回调
   */
  public void appendLogToTextarea(
      TextFlow textFlow,
      LogLevel logLevel,
      String logStr,
      Runnable afterRefreshCallback,
      Object... args) {
    // 由于需要处理UI相关的东西需要抛入UI线程进行处理
    Platform.runLater(
        () -> appendLogInFxThread(textFlow, logLevel, logStr, afterRefreshCallback, args));
  }

  /**
   * 通过fx线程将日志打印到窗口上
   *
   * @param logLevel 日志等级
   * @param originLogStr 日志信息
   * @param afterRefreshCallback 日志区域刷新之后的回调
   */
  private void appendLogInFxThread(
      TextFlow textFlow,
      LogLevel logLevel,
      String originLogStr,
      Runnable afterRefreshCallback,
      Object... args) {
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
          color = Color.TOMATO;
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
      if (afterRefreshCallback != null) {
        afterRefreshCallback.run();
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
    if (textFlow == null) {
      throw new IllegalArgumentException("not found log text area undertake, logStr: " + logStr);
    }
    String[] wrapStrArr = logStr.split("\n");
    for (int i = 0; i < wrapStrArr.length; i++) {
      AtomicInteger counter = logAreaRowCounter.get(textFlow);
      if (counter == null) {
        logAreaRowCounter.put(textFlow, new AtomicInteger());
      }
      if (logAreaRowCounter.get(textFlow).incrementAndGet()
          > DefaultEnvConfigConstant.LOG_AREA_MAX_SHOW_NUM) {
        if (textFlow.getChildren().size() > 0) {
          textFlow.getChildren().remove(0);
        }
        logAreaRowCounter.get(textFlow).decrementAndGet();
      }
    }
    Text text = new Text(buildLogData(logLevel, logStr) + "\n");
    text.setFont(new Font(13));
    text.setFill(color);
    textFlow.getChildren().add(text);
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
