package com.eouna.configtool.core.factory.config;

/**
 * bean处理时的钩子
 *
 * @author CCL
 */
public interface BeanPostHooker {

  /**
   * 在bean初始化之前调用
   *
   * @param bean bean实例
   * @param beanName bean名字
   * @return 修改之后的bean
   */
  default Object postBeforeInitialize(Object bean, String beanName) {
    return bean;
  }

  /**
   * 在bean初始化之后调用
   *
   * @param bean bean实例
   * @param beanName bean名字
   * @return 修改之后的bean
   */
  default Object postAfterInitialized(Object bean, String beanName) {
    return bean;
  }
}
