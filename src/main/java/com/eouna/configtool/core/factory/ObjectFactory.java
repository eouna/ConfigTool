package com.eouna.configtool.core.factory;
/**
 * 对象工厂
 *
 * @author CCL
 * @date 2023/9/15
 */
public interface ObjectFactory<T> {
  /**
   * 返回此工厂管理的对象实例
   *
   * @return 实例对象
   */
  T getObjectInstance();
}
