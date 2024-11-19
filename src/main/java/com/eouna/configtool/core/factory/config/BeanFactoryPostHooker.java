package com.eouna.configtool.core.factory.config;

import com.eouna.configtool.core.factory.support.AbstractAutowireBeanFactory;

/**
 * 此接口的作用: 用于在容器实例化Bean之前修改bean定义、属性值的添加、解决依赖关系和注册新的bean定义
 *
 * @author CCL
 */
public interface BeanFactoryPostHooker extends BeanPostHooker {

  /**
   * 在标准初始化之后修改应用程序上下文的内部bean工厂。<br>
   * 所有bean定义都将被加载，但还没有任何bean被实例化。<br>
   * 这允许重写或添加属性，甚至可以添加初始化的bean定义到工厂中。<br>
   *
   * @param beanFactory bean工厂
   */
  void postProcessorToBeanFactory(AbstractAutowireBeanFactory beanFactory);
}
