package com.eouna.configtool.utils;

import com.eouna.configtool.core.Ordered;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.context.ApplicationContextAware;
import com.eouna.configtool.core.factory.anno.Component;

/**
 * 上下文持有者
 *
 * @author CCL
 * @date 2023/9/21
 */
@Component
public class FxApplicationContextHolder implements ApplicationContextAware, Ordered {

  private static ApplicationContext applicationContext;

  public ApplicationContext getApplicationContext() {
    return applicationContext;
  }

  /**
   * 单例
   *
   * @return FxApplicationContextHolder
   */
  public static ApplicationContext getContext() {
    return applicationContext.getBean(FxApplicationContextHolder.class).getApplicationContext();
  }

  @Override
  public int getOrder() {
    return HIGHEST_ORDER;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    FxApplicationContextHolder.applicationContext = applicationContext;
  }
}
