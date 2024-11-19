package com.eouna.configtool.core.boot;

import javafx.application.Application.Parameters;

/**
 * 带命令行的运行回调
 *
 * @author CCL
 */
public interface ICommandLineRunner {

  /**
   * 运行回调
   *
   * @param parameters 程序参数
   */
  void run(Parameters parameters);
}
