package com.eouna.configtool.core.event;

import com.eouna.configtool.core.FxApplicationLoader;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.boot.env.CommandLineAndArgs;

/**
 * 程序启动完成
 *
 * @author CCL
 * @date 2023/7/7
 */
public class FxApplicationStartedEvent extends FxApplicationEvent {

  /** Context */
  private final ApplicationContext context;

  public FxApplicationStartedEvent(
      FxApplicationLoader fxApplicationLoader,
      CommandLineAndArgs args,
      ApplicationContext context) {
    super(fxApplicationLoader, args);
    this.context = context;
  }

  public ApplicationContext getApplicationContext() {
    return context;
  }
}
