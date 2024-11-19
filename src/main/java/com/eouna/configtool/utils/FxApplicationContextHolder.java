package com.eouna.configtool.utils;

import com.eouna.configtool.core.Ordered;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.event.FxApplicationStartedEvent;
import com.eouna.configtool.core.context.ApplicationListener;

/**
 * 上下文持有者
 *
 * @author CCL
 * @date 2023/9/21
 */
public class FxApplicationContextHolder implements ApplicationListener<FxApplicationStartedEvent>, Ordered {

  private ApplicationContext applicationContext;

  @Override
  public void onEventHappen(FxApplicationStartedEvent event) {
    applicationContext = event.getApplicationContext();
  }

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  /**
   * 单例
   *
   * @return FxApplicationContextHolder
   */
  public static FxApplicationContextHolder getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  @Override
  public int getOrder() {
    return HIGHEST_ORDER;
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final FxApplicationContextHolder instance;

    Singleton() {
      this.instance = new FxApplicationContextHolder();
    }

    public FxApplicationContextHolder getInstance() {
      return instance;
    }
  }
}
