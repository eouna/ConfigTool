package com.eouna.configtool.core.boot.context;

import com.eouna.configtool.core.context.AbstractApplicationContext;

/**
 * @author CCL
 */
@FunctionalInterface
public interface IApplicationContextInitializer<T extends AbstractApplicationContext> {

  /**
   * 上下文初始化接口
   *
   * @param applicationContext applicationContext
   */
  void initial(T applicationContext);
}
