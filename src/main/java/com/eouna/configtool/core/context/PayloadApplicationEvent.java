package com.eouna.configtool.core.context;

import com.eouna.configtool.core.annotaion.NoneNull;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.event.ApplicationEvent;

/**
 * @author CCL
 * @date 2023/9/27
 */
public class PayloadApplicationEvent<T> extends ApplicationEvent {

  private final T payload;

  /**
   * Constructs a prototypical Event.
   *
   * @param source the object on which the Event initially occurred
   * @throws IllegalArgumentException if source is null
   */
  public PayloadApplicationEvent(Object source, @NoneNull T payloadObject) {
    super(source);
    this.payload = payloadObject;
  }

  public T getPayload() {
    return payload;
  }
}
