package com.eouna.configtool.core.factory.support;

import com.eouna.configtool.core.factory.config.BeanFactoryPostHooker;

/**
 * bean定义注册Processor,用于动态注册bean和配置bean,可用于配置文件或者注解无法满足需求时,对bean定义进行重新修改
 *
 * @author CCL
 */
public interface BeanDefinitionRegistryPostHooker extends BeanFactoryPostHooker {

  /**
   * 在标准初始化后修改应用程序上下文的内部bean定义注册表。所有的常规bean定义都将被加载，但还没有任何bean被实例化。<br>
   * 这允许在下一个后处理阶段开始之前添加更多的bean定义。
   *
   * @param registry context的bean定义注册接口
   */
  void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry);
}
