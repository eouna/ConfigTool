package com.eouna.configtool.core.window;

/**
 * 多窗口接口
 *
 * @author CCL
 */
public interface IMultiWindow {

  /**
   * 获取每个窗口的标示符
   *
   * @return 窗口标示符
   */
  String generateWindowIdentifier();
}
