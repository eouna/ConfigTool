package com.eouna.configtool.core.context;

import com.eouna.configtool.core.Ordered;
import com.eouna.configtool.core.factory.support.AbstractAutowireBeanFactory;
import com.eouna.configtool.core.factory.support.BeanDefinitionRegistry;
import com.eouna.configtool.core.factory.support.BeanDefinitionRegistryPostHooker;

/**
 * @linkplain @Configure 注解的
 * @author CCL
 * @date 2023/9/21
 */
public class ConfigurationClassPostHooker implements BeanDefinitionRegistryPostHooker, Ordered {

  @Override
  public void postProcessorToBeanFactory(AbstractAutowireBeanFactory beanFactory) {}

  @Override
  public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {}

  @Override
  public int getOrder() {
    return LOWEST_ORDER;
  }
}
