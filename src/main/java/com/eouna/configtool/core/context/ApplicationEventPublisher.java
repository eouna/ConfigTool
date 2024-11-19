package com.eouna.configtool.core.context;

import java.util.EventObject;

import com.eouna.configtool.core.event.ApplicationEvent;

/**
 * 程序事件生产者
 *
 * @author CCL
 */
public interface ApplicationEventPublisher {

  /**
   * 事件推送
   *
   * @param applicationEvent 程序事件
   */
  void publishEvent(ApplicationEvent applicationEvent);

  /**
   * 推送事件
   *
   * @param eventObject 事件对象
   * @param <T> 事件对象源
   */
  <T extends EventObject> void publishEvent(T eventObject);
}
