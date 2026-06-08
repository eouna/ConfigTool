package com.eouna.configtool.core.event;

import com.eouna.configtool.core.FxApplicationLoader;
import com.eouna.configtool.core.boot.env.CommandLineAndArgs;

/**
 * 开始启动的事件
 *
 * @author CCL
 * @date 2023/7/6
 */
public class FxApplicationStartingEvent extends FxApplicationEvent {

  public FxApplicationStartingEvent(FxApplicationLoader fxApplicationLoader, CommandLineAndArgs args) {
    super(fxApplicationLoader, args);
  }
}
