package com.eouna.configtool.core.factory.support;

import com.eouna.configtool.core.factory.config.BeanDefinition;

/**
 * bean定义
 *
 * @author CCL
 * @date 2023/9/11
 */
public interface BeanDefinitionRegistry {

  /** 注册bean
   *
   * @param beanName bean名
   * @param beanDefinition bean定义
   */
  void registerBeanDefinition(String beanName, BeanDefinition beanDefinition);

  /**
   * 获取bean定义
   *
   * @param beanName bean名字
   * @return bean定义
   */
  BeanDefinition getBeanDefinition(String beanName);

  /**
   * 是否包含bean定义
   *
   * @param beanName beanName
   * @return 是否包含
   */
  boolean containBeanDefinition(String beanName);

  /**
   * 移除bean定义
   *
   * @param beanName bean名字
   */
  void removeBeanDefinition(String beanName);
}
