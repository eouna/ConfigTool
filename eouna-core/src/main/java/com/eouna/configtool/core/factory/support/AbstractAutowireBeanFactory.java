package com.eouna.configtool.core.factory.support;

import com.eouna.configtool.core.annotaion.AutoInject;
import com.eouna.configtool.core.context.event.EventListenerMethodBeanHooker;
import com.eouna.configtool.core.context.support.ApplicationContextAwareHooker;
import com.eouna.configtool.core.context.support.ApplicationListenerHooker;
import com.eouna.configtool.core.exceptions.InitConstructException;
import com.eouna.configtool.core.factory.bean.BeanDefinitionResource;
import com.eouna.configtool.core.factory.config.BeanDefinition;
import com.eouna.configtool.core.factory.config.BeanPostHooker;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.utils.BeanUtils;
import org.apache.commons.lang3.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽象自动注入bean工厂
 *
 * @author CCL
 * @date 2023/9/15
 */
public abstract class AbstractAutowireBeanFactory extends AbstractBeanFactory
    implements AutowireBeanFactory {

    /**
     * 是否允许循环引用
     */
    private boolean allCircleReference;

    /**
     * 接口和对应的实现子类
     */
    protected final Map<String, List<Object>> interfaceOfImpl = new ConcurrentHashMap<>(64);

    public boolean isAllCircleReference() {
        return allCircleReference;
    }

    public void setAllCircleReference(boolean allCircleReference) {
        this.allCircleReference = allCircleReference;
    }

    @Override
    public <T> T createBean(String beanClassName, BeanDefinition beanDefinition) {
        Class<?> beanClass = beanDefinition.getBeanClass();
        if (beanClass == null) {
            try {
                beanClass = Class.forName(beanClassName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            beanDefinition.setBeanClass(beanClass);
        }
        T singletonBean = (T) getSingleton(beanClassName);
        // 存在且获取的单例不为空
        if (singletonBean != null && isContainBean(beanClassName)) {
            return singletonBean;
        }
        // 组装bean定义(字段和方法的引用定义)
        wrapBeanDefinition(beanDefinition);
        // 创建原型bean
        T prototypeBean = createBeanPrototype(beanClassName, beanClass);
        // 初始化bean原型失败，没有找到合适的构造函数进行初始化
        if (prototypeBean == null) {
            return null;
        }
        // 添加实例获取工厂
        addSingletonFactory(beanClassName, () -> prototypeBean);
        applyBeanPostProcessorBeforeInitial(prototypeBean, beanClassName);
        // 初始化bean
        try {
            initializedBean(beanClassName, beanDefinition, prototypeBean);
        } catch (IllegalAccessException e) {
            LoggerUtils.getLogger().error("初始化bean：{} 失败", beanClassName);
            destroyBean(beanDefinition);
            throw new RuntimeException(e);
        }
        // 将bean添加进入实例对象中
        addSingleton(beanClassName, prototypeBean);
        // 获取一次，将早期引用删除
        T finalSingletonBean = (T) getSingleton(beanClassName);
        applyBeanPostProcessorAfterInitial(prototypeBean, beanClassName);
        if (finalSingletonBean == null) {
            logger.error("创建bean失败");
        } else {
            // 注册接口对应的实现
            List<Class<?>> beanAllInterfaces = ClassUtils.getAllInterfaces(finalSingletonBean.getClass());
            for (Class<?> anInterface : beanAllInterfaces) {
                interfaceOfImpl.computeIfAbsent(anInterface.getName(), k -> new ArrayList<>()).add(finalSingletonBean);
            }
        }
        return finalSingletonBean;
    }

    /**
     * 调用bean初始化之前钩子
     *
     * @param bean     bean
     * @param beanName 名字
     * @param <T>      T
     */
    private <T> void applyBeanPostProcessorBeforeInitial(T bean, String beanName) {
        List<BeanPostHooker> beanPostHookers = getPostHookers(BeanPostHooker.class);
        for (BeanPostHooker beanPostHooker : beanPostHookers) {
            if (beanPostHooker instanceof ApplicationListenerHooker applicationListenerHooker) {
                applicationListenerHooker.postBeforeInitialize(bean, beanName);
            }
            if (beanPostHooker instanceof ApplicationContextAwareHooker applicationContextAwareHooker) {
                applicationContextAwareHooker.postBeforeInitialize(bean, beanName);
            }
            if (beanPostHooker instanceof EventListenerMethodBeanHooker eventListenerMethodBeanHooker){
                eventListenerMethodBeanHooker.postBeforeInitialize(bean, beanName);
            }
        }
    }

    /**
     * 调用bean初始化之前钩子
     *
     * @param bean     bean
     * @param beanName 名字
     * @param <T>      T
     */
    private <T> void applyBeanPostProcessorAfterInitial(T bean, String beanName) {
        List<BeanPostHooker> beanPostHookers = getPostHookers(BeanPostHooker.class);
        for (BeanPostHooker beanPostHooker : beanPostHookers) {
            if (beanPostHooker instanceof ApplicationListenerHooker applicationListenerHooker) {
                applicationListenerHooker.postAfterInitialized(bean, beanName);
            }
            if (beanPostHookers instanceof ApplicationContextAwareHooker applicationContextAwareHooker) {
                applicationContextAwareHooker.postAfterInitialized(bean, beanName);
            }
            if (beanPostHooker instanceof EventListenerMethodBeanHooker eventListenerMethodBeanHooker){
                eventListenerMethodBeanHooker.postAfterInitialized(bean, beanName);
            }
        }
    }

    /**
     * 初始化bean，填充自动注入的bean字段和方法中的
     */
    private <T> void initializedBean(String beanClassName, BeanDefinition beanDefinition, T prototypeBean) throws IllegalAccessException {
        List<BeanDefinitionResource> beanDefinitionResources = beanDefinition.getAllBeanDefinitionResource();
        for (BeanDefinitionResource beanDefinitionResource : beanDefinitionResources) {
            Object resourceRef = beanDefinitionResource.getResourceRef();
            if (resourceRef instanceof Field field) {
                Type resourceType = beanDefinitionResource.getResourceType();
                // 尝试获取bean实例
                Object resourceBean = getBean((Class<?>) resourceType);
                field.setAccessible(true);
                field.set(prototypeBean, resourceBean);
            }
        }
        logger.trace("初始化bean：{}完成", beanClassName);
    }



    /**
     * 组装bean定义
     *
     * @param beanDefinition bean定义
     */
    protected void wrapBeanDefinition(BeanDefinition beanDefinition) {
        // 字段注入
        Field[] fields = beanDefinition.getBeanClass().getDeclaredFields();
        List<String> dependentOn = new ArrayList<>();
        for (Field field : fields) {
            if (field.isAnnotationPresent(AutoInject.class)) {
                Class<?> fieldClass = field.getType();
                String fieldClassName = fieldClass.getName();
                BeanDefinitionResource beanDefinitionResource = new BeanDefinitionResource();
                beanDefinitionResource.setResourceType(fieldClass);
                beanDefinitionResource.setResourceRef(field);
                beanDefinitionResource.setResourceName(fieldClassName);
                beanDefinition.addResource(beanDefinitionResource);
                dependentOn.add(fieldClassName);
            }
        }
        // 设置依赖
        beanDefinition.setDependOn(dependentOn.toArray(new String[0]));
        // 方法记录，记录方法上的注解
        for (Method declaredMethod : beanDefinition.getBeanClass().getDeclaredMethods()) {
            Annotation[] annotations = declaredMethod.getAnnotations();
            // 如果有注解，添加进去进行处理
            if(annotations.length > 0) {
                BeanDefinitionResource beanDefinitionResource = new BeanDefinitionResource();
                beanDefinitionResource.setResourceType(declaredMethod.getClass());
                beanDefinitionResource.setResourceRef(declaredMethod);
                beanDefinitionResource.setResourceName(declaredMethod.getName());
                beanDefinition.addResource(beanDefinitionResource);
            }
        }
    }

    /**
     * 创建bean的原型，只初始化对象
     *
     * @param beanClassName bean类名
     * @param beanClass     bean类
     * @param <T>           T
     * @return bean
     */
    protected <T> T createBeanPrototype(String beanClassName, Class<?> beanClass) {
        Constructor<?>[] beanClassConstructors = beanClass.getConstructors();
        for (Constructor<?> beanClassConstructor : beanClassConstructors) {
            if (beanClassConstructor.getParameterCount() == 0) {
                try {
                    Object b = BeanUtils.getClassInstance(beanClass);
                    addSingletonFactory(beanClassName, () -> b);
                    return (T) b;
                } catch (InitConstructException e) {
                    logger.error("初始化bean：{} 异常", beanClassName, e);
                    throw new RuntimeException(e);
                }
            } else {
                int parameterCount = 0;
                for (Parameter parameter : beanClassConstructor.getParameters()) {
                    if (!parameter.isAnnotationPresent(AutoInject.class)) {
                        parameterCount++;
                    }
                }
                if (parameterCount >= beanClassConstructor.getParameterCount()) {
                    continue;
                }
                Object[] args = new Object[beanClassConstructor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    if (beanClassConstructor.getParameters()[i].isAnnotationPresent(AutoInject.class)) {
                        args[i] = getBean(beanClassConstructor.getParameters()[i].getType());
                    }
                }
                try {
                    Object b = BeanUtils.getClassInstance(beanClass, args);
                    // 将获取出来的bean原型放入单例工厂
                    addSingletonFactory(beanClassName, () -> b);
                    return (T) b;
                } catch (InitConstructException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return null;
    }

}
