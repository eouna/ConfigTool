package com.eouna.configtool.core.event;

import com.eouna.configtool.core.context.AbstractApplicationContext;

/**
 * 基础context事件
 *
 * @author CCL
 * @date 2023/9/19
 */
public class ApplicationContextEvent extends ApplicationEvent {

  /**
   * Constructs a prototypical Event.
   *
   * @param source the object on which the Event initially occurred
   * @throws IllegalArgumentException if source is null
   */
  public ApplicationContextEvent(AbstractApplicationContext source) {
    super(source);
  }

  public AbstractApplicationContext getApplicationContext() {
    return (AbstractApplicationContext) getSource();
  }
}
