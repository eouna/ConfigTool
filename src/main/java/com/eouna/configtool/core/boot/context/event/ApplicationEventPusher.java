package com.eouna.configtool.core.boot.context.event;

import java.util.EventObject;

import com.eouna.configtool.core.event.ApplicationEvent;

/**
 * 容器内事件推送接口
 *
 * @author CCL
 */
public interface ApplicationEventPusher {

  /**
   * 向容器内推送事件
   *
   * @param applicationEvent 程序事件
   */
  default void pushEvent(ApplicationEvent applicationEvent) {
    pushEvent((EventObject) applicationEvent);
  }

  /**
   * 推送事件
   *
   * @param eventObject 事件对象
   */
  void pushEvent(EventObject eventObject);
}
