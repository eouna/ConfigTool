package com.eouna.configtool.common;

import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.event.FxApplicationContextLoadAfterEvent;
import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.context.ApplicationListener;

/**
 * context持有者
 *
 * @author CCL
 * @date 2023/7/18
 */
@Component
public class FxApplicationContextHolder
    implements ApplicationListener<FxApplicationContextLoadAfterEvent> {

  private ApplicationContext context;

  @Override
  public void onEventHappen(FxApplicationContextLoadAfterEvent event) {
    context = event.getApplicationContext();
  }

  /**
   * 单例
   *
   * @return FxApplicationContextHolder
   */
  public static FxApplicationContextHolder getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  public ApplicationContext getContext() {
    return context;
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
