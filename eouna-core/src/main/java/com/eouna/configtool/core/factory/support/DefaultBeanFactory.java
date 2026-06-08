package com.eouna.configtool.core.factory.support;

import com.eouna.configtool.core.context.ApplicationListener;
import com.eouna.configtool.core.context.support.ApplicationListenerHooker;
import com.eouna.configtool.core.factory.ListableBeanFactory;
import com.eouna.configtool.core.factory.config.BeanDefinition;
import com.eouna.configtool.core.factory.config.BeanPostHooker;
import org.apache.commons.lang3.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认类工厂
 *
 * @author CCL
 */
public class DefaultBeanFactory extends AbstractAutowireBeanFactory implements ListableBeanFactory {

    /**
     * bean定义缓存
     */
    private final Map<String, BeanDefinition> beanDefinitionCache = new ConcurrentHashMap<>(256);

    /**
     * 保存bean定义名列表 主要用于记录bean定义的初始化顺序
     */
    private final List<String> beanDefinitionNameList = new CopyOnWriteArrayList<>();

    /**
     * 名字对应的类
     */
    private final Map<String, Class<?>> nameOfClassMap = new ConcurrentHashMap<>(64);

    /**
     * 包内的注解对应的类列表
     */
    private static final Map<String, Set<Object>> annoOfBean = new ConcurrentHashMap<>();

    /**
     * 注册的钩子
     */
    private final List<BeanPostHooker> postHookers = new CopyOnWriteArrayList<>();

    public DefaultBeanFactory() {
    }

    /**
     * 通过注解拿到所有有此注解的类
     *
     * @param annotation 注解类
     * @return 类列表
     */
    public static Set<Object> getClassesOfAnno(Class<? extends Annotation> annotation) {
        return annoOfBean.get(annotation.getName());
    }

    @Override
    public <T> T createBean(Class<T> beanClass) {
        return createBean(beanClass.getName(), getBeanDefinition(beanClass.getName()));
    }


    @Override
    public <T> T createBean(String beanClassName, BeanDefinition beanDefinition) {
        T bean = super.createBean(beanClassName, beanDefinition);
        if (bean == null) {
            return null;
        }
        if (bean instanceof ApplicationListener) {
            List<ApplicationListenerHooker> applicationListenerHookers =
                getPostHookers(ApplicationListenerHooker.class);
            for (ApplicationListenerHooker applicationListenerHooker : applicationListenerHookers) {
                applicationListenerHooker.postAfterInitialized(bean, beanClassName);
            }
        }
        // 注册注解对应的类
        for (Annotation declaredAnnotation : bean.getClass().getDeclaredAnnotations()) {
            annoOfBean.computeIfAbsent(
                declaredAnnotation.annotationType().getName(), k -> new HashSet<>()).add(declaredAnnotation.annotationType());
        }
        return bean;
    }

    @Override
    public void registerBeanPostHooker(BeanPostHooker beanPostHooker) {
        if (beanPostHooker == null) {
            return;
        }
        synchronized (postHookers) {
            postHookers.remove(beanPostHooker);
            postHookers.add(beanPostHooker);
        }
    }

    @Override
    public <T extends BeanPostHooker> List<T> getPostHookers(Class<T> requireType) {
        return (List<T>) postHookers.stream().filter(beanPostHooker -> requireType.isAssignableFrom(beanPostHooker.getClass())).toList();
    }

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
        if (beanDefinitionCache.containsKey(beanName)) {
            // 默认先可以覆盖
            logger.trace("覆盖bean定义, beanName: {}", beanName);
        }
        beanDefinitionCache.put(beanName, beanDefinition);
        beanDefinitionNameList.add(beanName);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) {
        return beanDefinitionCache.get(beanName);
    }

    @Override
    public String[] getBeanDefinitionNames() {
        return beanDefinitionNameList.toArray(new String[0]);
    }

    @Override
    public boolean containBeanDefinition(String beanName) {
        return beanDefinitionCache.containsKey(beanName);
    }

    @Override
    public void removeBeanDefinition(String beanName) {
    }

    @Override
    public List<String> getBeanNamesForType(Class<?> classType) {
        List<String> beanDefinitionRegisteredNameList = new ArrayList<>();
        for (String beanDefinitionName : beanDefinitionNameList) {
            BeanDefinition beanDefinition = beanDefinitionCache.get(beanDefinitionName);
            Class<?> beanClass = beanDefinition.getBeanClass();
            if (classType.isAssignableFrom(beanClass) || classType == beanClass) {
                beanDefinitionRegisteredNameList.add(beanDefinitionName);
            }
        }
        return beanDefinitionRegisteredNameList;
    }

    @Override
    public <T> List<T> getBeansForType(Class<?> classType) {
        return (List<T>) interfaceOfImpl.getOrDefault(classType.getName(), new ArrayList<>());
    }

    @Override
    public void preInstantiateSingletons() {
        // 遍历所有bean定义名列表，并尝试获取bean实例
        for (String beanName : beanDefinitionNameList) {
            getBean(beanName);
        }
    }

    @Override
    public int getBeanListSize() {
        return sizeOfSingletonBean();
    }
}
