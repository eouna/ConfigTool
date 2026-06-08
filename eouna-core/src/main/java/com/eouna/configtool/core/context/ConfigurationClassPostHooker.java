package com.eouna.configtool.core.context;

import com.eouna.configtool.core.Ordered;
import com.eouna.configtool.core.annotaion.FxApplication;
import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.factory.bean.AnnotationBeanDefinition;
import com.eouna.configtool.core.factory.bean.GeneralBeanDefinition;
import com.eouna.configtool.core.factory.bean.RootDefinitionBean;
import com.eouna.configtool.core.factory.config.BeanDefinition;
import com.eouna.configtool.core.factory.support.AbstractAutowireBeanFactory;
import com.eouna.configtool.core.factory.support.BeanDefinitionRegistry;
import com.eouna.configtool.core.factory.support.BeanDefinitionRegistryPostHooker;
import com.eouna.configtool.core.utils.ClassOfPackageUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;

/**
 * @author CCL
 * @linkplain @Configure 注解的
 * @date 2023/9/21
 */
public class ConfigurationClassPostHooker implements BeanDefinitionRegistryPostHooker, Ordered {

    private AbstractAutowireBeanFactory abstractAutowireBeanFactory;

    @Override
    public void postProcessorToBeanFactory(AbstractAutowireBeanFactory beanFactory) {
        this.abstractAutowireBeanFactory = beanFactory;
    }

    private boolean checkNeedLoadClass(Class<?> beanDefinitionClass) {
        Annotation[] annotations = beanDefinitionClass.getDeclaredAnnotations();
        return Arrays.stream(annotations).anyMatch(annotation ->
            annotation.annotationType().equals(Component.class) ||
                annotation.annotationType().equals(FxApplication.class)
        );
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        String[] beanDefinitionNames = registry.getBeanDefinitionNames();
        for (String beanDefinitionName : beanDefinitionNames) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanDefinitionName);
            if (!(beanDefinition instanceof RootDefinitionBean)) {
                continue;
            }
            Class<?> beanClass = beanDefinition.getBeanClass();
            if (checkNeedLoadClass(beanClass)) {
                String packageName = beanClass.getPackage().getName();
                try {
                    Set<Class<?>> classes =
                        ClassOfPackageUtils.getClassesByPackage(beanClass.getClassLoader(), packageName);
                    for (Class<?> aClass : classes) {
                        registryAnnoClassBeanDefinition(registry, aClass);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * 带注解类的bean注册
     *
     * @param registry  bean注册器
     * @param beanClass bean类
     */
    protected void registryAnnoClassBeanDefinition(BeanDefinitionRegistry registry, Class<?> beanClass) {
        // 扫描
        if (!beanClass.isAnnotationPresent(Component.class)) {
            return;
        }
        // 内部类检测
        Class<?>[] declaredClasses = beanClass.getDeclaredClasses();
        for (Class<?> declaredClass : declaredClasses) {
            registryAnnoClassBeanDefinition(registry, declaredClass);
        }
        AnnotationBeanDefinition generalBeanDefinition = new AnnotationBeanDefinition();
        generalBeanDefinition.setBeanClassName(beanClass.getName());
        generalBeanDefinition.setBeanClass(beanClass);
        registry.registerBeanDefinition(beanClass.getName(), generalBeanDefinition);
    }

    @Override
    public int getOrder() {
        return LOWEST_ORDER;
    }
}
