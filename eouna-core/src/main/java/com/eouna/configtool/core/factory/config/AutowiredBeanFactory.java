package com.eouna.configtool.core.factory.config;

/**
 * 自动装配类的bean工厂
 *
 * @author CCL
 */
public interface AutowiredBeanFactory {

  /**
   * 创建bean 当字段注解自动装配的注解时会使用创建bean接口
   *
   * @param beanClass bean类
   * @return gentemppath.bean
   * @param <T> beanObj
   */
  <T> T createBean(Class<?> beanClass);

  /**
   * 销毁bean
   *
   * @param beanClass bean类
   */
  void destroyBean(Class<?> beanClass);
}
