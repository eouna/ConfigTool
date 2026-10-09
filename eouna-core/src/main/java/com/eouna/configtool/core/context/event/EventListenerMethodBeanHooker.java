package com.eouna.configtool.core.context.event;

import com.eouna.configtool.core.annotaion.EventSubscriber;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.context.AbstractApplicationContext;
import com.eouna.configtool.core.factory.bean.BeanDefinitionResource;
import com.eouna.configtool.core.factory.config.BeanDefinition;
import com.eouna.configtool.core.factory.config.BeanFactoryPostHooker;
import com.eouna.configtool.core.factory.config.BeanPostHooker;
import com.eouna.configtool.core.factory.support.AbstractAutowireBeanFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author CCL
 * @version : [v1.0]
 * @className : [EventListenerMethodBeanHooker]
 * @description :  描述
 * @createTime : [2026/5/12 14:40]
 */
public class EventListenerMethodBeanHooker implements BeanPostHooker {

    private final AbstractApplicationContext applicationContext;

    private AbstractAutowireBeanFactory beanFactory;

    public EventListenerMethodBeanHooker(AbstractApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postAfterInitialized(Object bean, String beanName) {
        BeanDefinition beanDefinition = applicationContext.getBeanFactory().getBeanDefinition(beanName);
        // 部分bean(如直接注册的实例/内部bean)没有对应的bean定义,跳过监听方法扫描
        if (beanDefinition == null) {
            return bean;
        }
        List<BeanDefinitionResource> beanDefinitionResource = beanDefinition.getAllBeanDefinitionResource();
        for (BeanDefinitionResource definitionResource : beanDefinitionResource) {
            if (definitionResource.getResourceRef() instanceof Method method
                && method.getAnnotation(EventSubscriber.class) != null) {
                BeanEventMethodListenerAdapter eventMethodListenerAdapter =
                    new BeanEventMethodListenerAdapter(beanName, bean.getClass(), method);
                eventMethodListenerAdapter.init(applicationContext);
                this.applicationContext.addApplicationListener(eventMethodListenerAdapter);
            }
        }
        return bean;
    }
}
