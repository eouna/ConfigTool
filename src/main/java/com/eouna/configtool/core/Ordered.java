package com.eouna.configtool.core;

/**
 * 通用顺序接口, 顺序优先级从负值到正值优先级依次减小
 *
 * @author CCL
 */
public interface Ordered {

  int HIGHEST_ORDER = Integer.MIN_VALUE;

  int LOWEST_ORDER = Integer.MAX_VALUE;

  /**
   * 获取优先级
   *
   * @return 优先级
   */
  int getOrder();
}
