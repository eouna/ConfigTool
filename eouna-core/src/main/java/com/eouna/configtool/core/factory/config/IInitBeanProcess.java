package com.eouna.configtool.core.factory.config;

/**
 * bean初始化过程
 *
 * @author CCL
 */
public interface IInitBeanProcess<T> {

  /**
   * bean初始化之前
   *
   * @param bean gentemppath.bean
   * @return T 修改后的值
   */
  T beforeBeanInit(T bean);

  /**
   * bean初始化之后
   *
   * @param bean gentemppath.bean
   * @return T 修改后的值
   */
  T afterBeanInit(T bean);
}
