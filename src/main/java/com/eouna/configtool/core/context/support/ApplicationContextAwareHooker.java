package com.eouna.configtool.core.context.support;

import com.eouna.configtool.core.context.AbstractApplicationContext;
import com.eouna.configtool.core.context.ApplicationEventPublisher;
import com.eouna.configtool.core.context.ApplicationEventPublisherAware;
import com.eouna.configtool.core.factory.Aware;
import com.eouna.configtool.core.factory.config.BeanPostHooker;

/**
 * 管理和调用实现aware接口的类于context上下文数据之间的联系,例如设置context中的环境和bean工厂及其他数据
 *
 * @author CCL
 * @date 2023/9/27
 */
public class ApplicationContextAwareHooker implements BeanPostHooker {

  final AbstractApplicationContext applicationContext;

  public ApplicationContextAwareHooker(AbstractApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * 初始化之前向bean设置context中的一些数据
   *
   * @param bean bean实例
   * @param beanName bean名字
   * @return gentemppath.bean
   */
  @Override
  public Object postBeforeInitialize(Object bean, String beanName) {
    if (!(bean instanceof Aware)) {
      return bean;
    }
    if (bean instanceof ApplicationEventPublisherAware) {
      ((ApplicationEventPublisherAware) bean).setApplicationEventPublisher(this.applicationContext);
    }
    return bean;
  }
}
