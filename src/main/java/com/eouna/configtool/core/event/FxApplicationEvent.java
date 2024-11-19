package com.eouna.configtool.core.event;

import com.eouna.configtool.core.FxApplicationLoader;
import com.eouna.configtool.core.boot.env.CommandLineAndArgs;
import javafx.application.Application.Parameters;

/**
 * fx程序基础程序事件类
 *
 * @author CCL
 * @date 2023/7/6
 */
public class FxApplicationEvent extends ApplicationEvent {

  /** 程序参数 */
  private final CommandLineAndArgs args;

  public FxApplicationEvent(FxApplicationLoader fxApplicationLoader, CommandLineAndArgs args) {
    super(fxApplicationLoader);
    this.args = args;
  }

  @Override
  public FxApplicationLoader getWrappedSource() {
    return (FxApplicationLoader) getSource();
  }

  public CommandLineAndArgs getArgs() {
    return args;
  }
}
