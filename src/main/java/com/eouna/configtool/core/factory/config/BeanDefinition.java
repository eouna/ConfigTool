package com.eouna.configtool.core.factory.config;

import java.util.List;

/**
 * Bean定义
 *
 * @author CCL
 */
public interface BeanDefinition {

  /**
   * 设置父定义名字
   *
   * @param parentName 父定义名
   */
  void setParentName(String parentName);

  /**
   * 获取父定义名
   *
   * @return 父定义名
   */
  String getParentName();

  /**
   * 设置bean类名
   *
   * @param beanClassName bean类名
   * @return bean类名
   */
  String setBeanClassName(String beanClassName);

  /**
   * 获取bean的类
   *
   * @return 类
   */
  Class<?> getBeanClass();



  /**
   * 设置bean类
   *
   * @param beanClass 设置bean类
   */
  void setBeanClass(Class<?> beanClass);

  /**
   * 设置bean作用域
   *
   * @param scope 作用域
   */
  void setScope(EBeanIdentifierScope scope);

  /**
   * 获取作用域范围
   *
   * @return 作用域范围
   */
  EBeanIdentifierScope getScope();

  /**
   * 设置bean所需要的依赖,如果有优先加载
   *
   * @param beanClassName 依赖
   */
  void setDependOn(String... beanClassName);

  /**
   * 获取依赖列表
   *
   * @return 依赖列表
   */
  List<String> getDependOnList();
}
