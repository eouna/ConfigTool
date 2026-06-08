package com.eouna.configtool.core.factory;

import com.eouna.configtool.core.boot.convert.ApplicationConverters;

import java.util.List;

/**
 * bean工厂接口
 *
 * @author CCL
 */
public interface IBeanFactory {

  /**
   * 获取bean
   *
   * @param beanClass bean类
   * @return 容器内的bean实例
   * @param <T> T
   */
  <T> T getBean(Class<T> beanClass);

  /**
   * 获取bean
   *
   * @param beanClassName bean类名
   * @return 容器内的bean实例
   * @param <T> T
   */
  <T> T getBean(String beanClassName);


  /**
   * 获取bean
   *
   * @param beanClassName bean类名
   * @param requiredType 指定的类型
   * @return 容器内的bean实例
   * @param <T> T
   */
  <T> T getBean(String beanClassName, Class<T> requiredType);

  /**
   * 通过类型获取bean
   *
   * @param typeClass 类型
   * @return 对应的bean
   */
  <T> List<T> getBeansForType(Class<?> typeClass);

  /**
   * 卸载bean
   *
   * @param beanClass bean类
   */
  void unloadBean(Class<?> beanClass);

  /**
   * 卸载bean
   *
   * @param beanClassName bean类名
   */
  void unloadBean(String beanClassName);

  /**
   * 设置转换器
   *
   * @param converter convert
   */
  void setConverter(ApplicationConverters converter);
}
