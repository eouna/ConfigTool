package com.eouna.configtool.core.boot.configure;

import com.eouna.configtool.core.Ordered;
import com.eouna.configtool.core.boot.context.IApplicationContextInitializer;
import com.eouna.configtool.core.context.AbstractApplicationContext;
import com.eouna.configtool.core.context.AnnotationApplicationContext;
import com.eouna.configtool.core.context.ConfigurationClassPostHooker;
import com.eouna.configtool.core.factory.bean.AbstractBeanDefinition;
import com.eouna.configtool.core.factory.config.BeanDefinition;
import com.eouna.configtool.core.factory.support.AbstractAutowireBeanFactory;
import com.eouna.configtool.core.factory.support.BeanDefinitionRegistry;
import com.eouna.configtool.core.factory.support.BeanDefinitionRegistryPostHooker;
import com.eouna.configtool.core.utils.ClassUtils;

/**
 * 带缓存的bean元数据读取初始化类,用于初始化全局的 MetaDataReaderFactory
 *
 * @author CCL
 * @date 2023/9/28
 */
public class CachedMetaDataReaderFactoryContextInitializer
    implements IApplicationContextInitializer<AnnotationApplicationContext> {
  @Override
  public void initial(AnnotationApplicationContext applicationContext) {
    // 添加元数据读取处理钩子
    applicationContext.addBeanFactoryPostHooker(
        new CachingMetaDataReaderFactoryPostHooker(applicationContext));
  }

  /** 此钩子优先级为最高,需要预先处理类的一系列数据,并缓存 */
  public static class CachingMetaDataReaderFactoryPostHooker
      implements BeanDefinitionRegistryPostHooker, Ordered {

    private final AbstractApplicationContext context;

    public CachingMetaDataReaderFactoryPostHooker(AbstractApplicationContext context) {
      this.context = context;
    }

    public AbstractApplicationContext getContext() {
      return context;
    }

    @Override
    public void postProcessorToBeanFactory(AbstractAutowireBeanFactory beanFactory) {
      String configurationAnnoBeanClassName = ClassUtils.getClassFullName(ConfigurationClassPostHooker.class);
      BeanDefinition beanDefinition = beanFactory.getBeanDefinition(configurationAnnoBeanClassName);
      if(beanDefinition instanceof AbstractBeanDefinition){
        beanDefinition.getBeanClass();
      }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {}

    @Override
    public int getOrder() {
      return HIGHEST_ORDER;
    }
  }
}
