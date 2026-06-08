package com.eouna.configtool.core.event;

import com.eouna.configtool.core.FxApplicationLoader;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.boot.env.CommandLineAndArgs;

/**
 * 程序上下文加载完成 bean完成装配
 *
 * @author CCL
 * @date 2023/7/7
 */
public class FxApplicationContextLoadAfterEvent extends FxApplicationEvent {

  private final ApplicationContext context;

  public FxApplicationContextLoadAfterEvent(
      FxApplicationLoader fxApplicationLoader, CommandLineAndArgs args, ApplicationContext context) {
    super(fxApplicationLoader, args);
    this.context = context;
  }

  public ApplicationContext getApplicationContext() {
    return context;
  }
}
