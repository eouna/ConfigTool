package com.eouna.configtool.core.logger;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.text.TextFlow;

/**
 * 步數日志器
 *
 * @author CCL
 * @date 2023/4/6
 */
public class TextAreaStepLogger extends TextAreaLogger {

  public TextAreaStepLogger(TextFlow specifyLoggerArea) {
    super(specifyLoggerArea);
  }

  /** 步数计数器 */
  private final AtomicInteger stepper = new AtomicInteger(0);

  protected String decorateMsg(String msg) {
    int stepCount = stepper.incrementAndGet();
    return "[step" + stepCount + "]#" + msg;
  }

  @Override
  public void info(String msg, Object... arrays) {
    super.info(decorateMsg(msg), arrays);
  }

  @Override
  public void success(String msg, Object... arrays) {
    super.success(decorateMsg(msg), arrays);
  }

  public void reset(){
    stepper.set(0);
  }
}
