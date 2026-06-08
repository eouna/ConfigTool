package com.eouna.configtool.core.factory.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.eouna.configtool.core.boot.convert.ApplicationConverters;
import com.eouna.configtool.core.factory.IBeanFactory;
import com.eouna.configtool.core.factory.bean.GeneralBeanDefinition;
import com.eouna.configtool.core.factory.bean.RootDefinitionBean;
import com.eouna.configtool.core.factory.config.BeanDefinition;
import com.eouna.configtool.core.factory.config.EBeanIdentifierScope;
import com.eouna.configtool.core.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 抽向bean工厂
 *
 * @author CCL
 * @date 2023/7/25
 */
public abstract class AbstractBeanFactory extends DefaultSingletonBeanRegistry
    implements IBeanFactory {

    /**
     * logger
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 转换器
     */
    protected ApplicationConverters converters;

    /**
     * bean开始创建的标识
     */
    private AtomicBoolean beanCreationStartFlag;

    /**
     * 合并的bean定义缓存
     */
    private final Map<String, RootDefinitionBean> mergedBeanDefinitions =
        new ConcurrentHashMap<>(256);

    @Override
    public void setConverter(ApplicationConverters converter) {
        this.converters = converter;
    }

    @Override
    public <T> T getBean(Class<T> beanClass) {
        return doGetBean(beanClass.getName(), (Class<? extends T>) beanClass);
    }

    @Override
    public <T> T getBean(String beanClassName) {
        return doGetBean(beanClassName, null);
    }

    @Override
    public <T> T getBean(String beanClassName, Class<T> requiredType) {
        return doGetBean(beanClassName, requiredType);
    }

    private <T> T doGetBean(String beanClassName, Class<T> classType) {
        Object createdSingleton = getSingleton(beanClassName);
        if (createdSingleton != null && (classType == null || classType.isAssignableFrom(createdSingleton.getClass()))) {
            return (T) createdSingleton;
        } else if (createdSingleton == null) {
            // 初始化bean
            BeanDefinition beanDefinition = getMergedBeanDefinition(beanClassName);
            if (beanDefinition == null) {
                beanDefinition = new GeneralBeanDefinition();
                beanDefinition.setBeanClassName(beanClassName);
            }
            if (beanDefinition.getDependOnList() != null) {
                for (String dependentBeanName : beanDefinition.getDependOnList()) {
                    if (isDependent(beanClassName, dependentBeanName)) {
                        throw new IllegalStateException("循环引用");
                    }
                    // 注册bean依赖
                    registerBeanDependency(dependentBeanName, beanClassName);
                    try {
                        // 尝试对依赖bean进行初始化和加载
                        getBean(dependentBeanName);
                    } catch (Exception e) {
                        throw new IllegalStateException(
                            "初始化bean: " + beanClassName + " 的依赖项: " + dependentBeanName + " 失败");
                    }
                }
            }
            EBeanIdentifierScope scope = beanDefinition.getScope();
            switch (scope) {
                case PROTOTYPE:
                    Object prototypeInstance = createBean(beanClassName, beanDefinition);
                    createdSingleton =
                        getObjectForBeanInstance(prototypeInstance, beanClassName, beanDefinition);
                    break;
                case SINGLETON:
                    createdSingleton = getSingleton(beanClassName);
                    if (createdSingleton == null) {
                        createdSingleton = createBean(beanClassName, beanDefinition);
                    }
                    break;
                default:
                    break;
            }
        }
        // 是否是请求的类型
        if (classType == null || classType.isAssignableFrom(createdSingleton.getClass())) {
            return (T) createdSingleton;
        }
        // 适配目标请求
        return adaptBeanInstance(beanClassName, createdSingleton, classType);
    }

    private Object getObjectForBeanInstance(
        Object prototypeInstance, String beanClassName, BeanDefinition beanDefinition) {
        return prototypeInstance;
    }

    private <T> T adaptBeanInstance(String beanClassName, Object bean, Class<T> requireType) {
        return (T) bean;
    }

    protected BeanDefinition getMergedBeanDefinition(String beanName) {
        BeanDefinition beanDefinition = mergedBeanDefinitions.get(beanName);
        if (beanDefinition != null) {
            return beanDefinition;
        }
        beanDefinition = getBeanDefinition(beanName);
        if (beanDefinition != null) {
            return beanDefinition;
        }
        GeneralBeanDefinition generalBeanDefinition = new GeneralBeanDefinition();
        generalBeanDefinition.setBeanClassName(beanName);
        return generalBeanDefinition;
    }

    /**
     * 获取已经缓存的bean定义
     *
     * @param beanName 名字
     * @return bean定义
     */
    public abstract BeanDefinition getBeanDefinition(String beanName);

    public void addMergedBeanDefinition(String beanName, BeanDefinition beanDefinition) {
        mergedBeanDefinitions.put(beanName, (RootDefinitionBean) beanDefinition);
    }

    @Override
    public void unloadBean(Class<?> beanClass) {
    }

    @Override
    public void unloadBean(String beanClassName) {
    }

    /**
     * 创建bean
     *
     * @param beanClass      bean类
     * @param definitionBean bean定义
     * @param <T>            T
     * @return 创建后的bean
     */
    public abstract <T> T createBean(String beanClass, BeanDefinition definitionBean);

    /**
     * 销毁bean
     *
     * @param existingBean 已存在的bean
     */
    public void destroyBean(Object existingBean) {

    }

    /**
     * bean是否开始创建
     *
     * @return 是否创建
     */
    protected boolean hasBeanStartedCreation() {
        return beanCreationStartFlag.get();
    }

    public void setBeanCreatStart() {
        beanCreationStartFlag.compareAndSet(false, true);
    }
}
