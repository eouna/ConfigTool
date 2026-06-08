package com.eouna.configtool.core.event;

import java.util.EventObject;

import com.google.common.base.Objects;

/**
 * 程序事件
 *
 * @author CCL
 * @date 2023/6/20
 */
public class ApplicationEvent extends EventObject {

  /** 事件开始时间 */
  private final long startTime;

  /**
   * Constructs a prototypical Event.
   *
   * @param source the object on which the Event initially occurred
   * @throws IllegalArgumentException if source is null
   */
  public ApplicationEvent(Object source) {
    super(source);
    startTime = System.currentTimeMillis();
  }

  public <T> T getWrappedSource() {
    return (T) super.getSource();
  }

  public long getStartTime() {
    return startTime;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ApplicationEvent that)) return false;
    return getStartTime() == that.getStartTime();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getStartTime());
  }
}
