package com.eouna.configtool.core.context;


import com.eouna.configtool.core.event.ApplicationEvent;

/**
 * 程序监听器 K event中的参数类型 E event
 *
 * @author CCL
 */
public interface ApplicationListener<E extends ApplicationEvent> {

  /**
   * 事件发生
   *
   * @param event 发生的具体事件
   */
  void onEventHappen(E event);
}
