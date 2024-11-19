package com.eouna.configtool.core.boot;

import java.util.List;

import com.eouna.configtool.core.boot.context.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 程序启动时的监听器的调用类
 *
 * @author CCL
 * @date 2023/7/4
 */
public class ApplicationBootListenersHolder {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final List<IApplicationBootListener> listeners;

  public ApplicationBootListenersHolder(List<IApplicationBootListener> listeners) {
    this.listeners = listeners;
  }

  public void starting() {
    for (IApplicationBootListener listener : this.listeners) {
      listener.staring();
    }
  }

  public void contextPrepared(ApplicationContext context) {
    for (IApplicationBootListener listener : this.listeners) {
      listener.contextLoadBefore(context);
    }
  }

  public void contextLoaded(ApplicationContext context) {
    for (IApplicationBootListener listener : this.listeners) {
      listener.contextLoadAfter(context);
    }
  }

  public void started(ApplicationContext context) {
    for (IApplicationBootListener listener : this.listeners) {
      listener.started(context);
    }
  }

  public void running(ApplicationContext context) {
    for (IApplicationBootListener listener : this.listeners) {
      listener.running(context);
    }
  }

  public void failed(ApplicationContext context, Throwable exception) {
    for (IApplicationBootListener listener : this.listeners) {
      callFailedListener(listener, context, exception);
    }
  }

  private void callFailedListener(
      IApplicationBootListener listener, ApplicationContext context, Throwable exception) {
    try {
      listener.failed(context, exception);
    } catch (Throwable ex) {
      if (exception == null) {
        if (ex instanceof RuntimeException) {
          throw (RuntimeException) ex;
        }
        throw (Error) ex;
      }
      String message = ex.getMessage();
      message = (message != null) ? message : "没有错误信息";
      this.logger.error("广播失败事件时失败 (" + message + ")", ex);
    }
  }
}
