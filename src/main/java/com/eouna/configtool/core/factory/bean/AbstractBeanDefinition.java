package com.eouna.configtool.core.factory.bean;

import com.eouna.configtool.core.factory.config.BeanDefinition;
import com.eouna.configtool.core.factory.config.EBeanIdentifierScope;

/**
 * 抽象bean定义
 *
 * @author CCL
 * @date 2023/9/22
 */
public abstract class AbstractBeanDefinition implements BeanDefinition {

  /** 原始bean class */
  private Class<?> beanClass;

  protected EBeanIdentifierScope scope;

  @Override
  public void setBeanClass(Class<?> beanClass) {
    this.beanClass = beanClass;
  }

  @Override
  public Class<?> getBeanClass() {
    return beanClass;
  }

  @Override
  public String setBeanClassName(String beanClassName) {
    return null;
  }

  @Override
  public void setScope(EBeanIdentifierScope scope) {}

  public boolean isSingleton(String scope) {
    return EBeanIdentifierScope.SINGLETON.name().equalsIgnoreCase(scope);
  }
}
