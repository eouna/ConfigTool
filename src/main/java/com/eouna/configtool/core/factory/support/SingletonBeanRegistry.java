package com.eouna.configtool.core.factory.support;

/**
 * 管理单例bean,用于定义单例bean的注册和获取
 *
 * @author CCL
 */
public interface SingletonBeanRegistry {

  /**
   * 注册单例bean
   *
   * @param beanName bean名
   * @param beanObject bean实例
   */
  void registerSingletonBean(String beanName, Object beanObject);

  /**
   * 获取单例
   *
   * @param beanName bean名
   */
  Object getSingleton(String beanName);

  /**
   * 是否包含bean
   *
   * @param beanName beanName
   * @return 是否包含
   */
  boolean isContainBean(String beanName);

  /**
   * 获取bean容器的互斥量
   *
   * @return mutex object
   */
  Object getBeanContainerMutex();
}
