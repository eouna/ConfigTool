package com.eouna.configtool.core.factory.support;

import com.eouna.configtool.core.factory.ListableBeanFactory;
import com.eouna.configtool.core.factory.config.BeanDefinition;
import com.eouna.configtool.core.factory.config.BeanPostHooker;
import com.eouna.configtool.core.utils.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认类工厂
 *
 * @author CCL
 */
public class DefaultBeanFactory extends AbstractAutowireBeanFactory implements ListableBeanFactory {

  /** bean定义缓存 */
  private final Map<String, BeanDefinition> beanDefinitionCache = new ConcurrentHashMap<>(256);

  /** 保存bean定义名列表 主要用于记录bean定义的初始化顺序 */
  private final List<String> beanDefinitionNameList = new CopyOnWriteArrayList<>();

  /** 名字对应的类 */
  private final Map<String, Class<?>> nameOfClassMap = new ConcurrentHashMap<>(64);

  /** 包内的注解对应的类列表 */
  private static final Map<String, Set<Class<?>>> ANNOTATION_CLASS_MAP = new ConcurrentHashMap<>();

  /** */
  private final List<BeanPostHooker> postHookers = new CopyOnWriteArrayList<>();

  public DefaultBeanFactory() {}

  /**
   * 通过注解拿到所有有此注解的类
   *
   * @param annotation 注解类
   * @return 类列表
   */
  public static Set<Class<?>> getClassesOfAnno(Class<? extends Annotation> annotation) {
    return ANNOTATION_CLASS_MAP.get(annotation.getSimpleName());
  }

  /** 构建游戏服包下所有注解和类的关系 */
  private void buildAllAnnoClassRelation(Set<Class<?>> classesByPackage) {
    for (Class<?> aClass : classesByPackage) {
      putClassToRelationMap(aClass);
    }
  }

  private void registerAllClass() {}

  /**
   * 将类上的注解和类构建一个关联
   *
   * @param aClass 目标类
   */
  private static void putClassToRelationMap(Class<?> aClass) {
    assert aClass != null;
    // 本身是注解类的跳过
    if (aClass.isAnnotation()) {
      return;
    }
    Annotation[] annotations = aClass.getAnnotations();
    Class<?>[] innerClasses = aClass.getDeclaredClasses();
    if (innerClasses.length > 0) {
      // 如果存在内部类需要继续
      for (Class<?> innerClass : innerClasses) {
        putClassToRelationMap(innerClass);
      }
    } else {
      // 内部类
      if (annotations.length > 0) {
        for (Annotation annotation : annotations) {
          ANNOTATION_CLASS_MAP
              .computeIfAbsent(
                  annotation.annotationType().getSimpleName(),
                  k ->
                      new ConcurrentSkipListSet<>(
                          Comparator.comparing((Class<?> o) -> o.getName())))
              .add(aClass);
        }
      }
    }
  }

  @Override
  public <T> T createBean(Class<T> beanClass) {
    return null;
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
    return null;
  }

  @Override
  public boolean containBeanDefinition(String beanName) {
    return false;
  }

  @Override
  public void removeBeanDefinition(String beanName) {}

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
  public <T> T createBean(String beanClass, BeanDefinition definitionBean) {
    return null;
  }
}
