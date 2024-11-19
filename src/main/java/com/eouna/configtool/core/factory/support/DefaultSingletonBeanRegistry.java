package com.eouna.configtool.core.factory.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.eouna.configtool.core.factory.ObjectFactory;

/**
 * 单例bean管理器
 *
 * @author CCL
 * @date 2023/9/15
 */
public class DefaultSingletonBeanRegistry implements SingletonBeanRegistry {

  /** 单例缓存 */
  private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

  /** 早期单例对象的缓存 */
  private final Map<String, Object> earlySingletonObject = new ConcurrentHashMap<>(16);

  /** 单例工厂 */
  private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
  /** 依赖列表 */
  private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);
  /** 依赖列表 */
  private final Map<String, Set<String>> beanDependentMap = new ConcurrentHashMap<>(64);

  /** 处于正在创建的单例bean列表. */
  private final Set<String> singletonsCurrentlyInCreation =
      Collections.newSetFromMap(new ConcurrentHashMap<>(16));

  @Override
  public void registerSingletonBean(String beanName, Object beanObject) {
    Objects.requireNonNull(beanName, "gentemppath.bean name not allowed null");
    Objects.requireNonNull(beanObject, "gentemppath.bean name not allowed null");
    synchronized (singletonObjects) {
      if (singletonObjects.containsKey(beanName)) {
        throw new IllegalStateException("the " + beanName + " has been registered");
      }
    }
  }

  protected void addSingleton(String beanName, Object instanceObj) {
    synchronized (singletonObjects) {
      singletonObjects.put(beanName, instanceObj);
      earlySingletonObject.remove(beanName);
    }
  }

  @Override
  public Object getSingleton(String beanName) {
    return getSingleton(beanName, true);
  }

  /**
   * 保存bean对应的单例工厂
   *
   * @param beanName gentemppath.bean
   * @param singletonFactory 单例工厂
   */
  protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
    Objects.requireNonNull(singletonFactory, "Singleton factory must not be null");
    synchronized (this.singletonObjects) {
      if (!this.singletonObjects.containsKey(beanName)) {
        singletonFactories.put(beanName, singletonFactory);
        earlySingletonObject.remove(beanName);
      }
    }
  }

  /**
   * 获取单例
   *
   * @param beanName bean名
   * @param allowUseEarlyReference 是否允许使用早期引用
   * @return
   */
  public Object getSingleton(String beanName, boolean allowUseEarlyReference) {
    Object instance = singletonObjects.get(beanName);
    if (instance == null && singletonsCurrentlyInCreation.contains(beanName)) {
      instance = earlySingletonObject.get(beanName);
      if (instance == null && allowUseEarlyReference) {
        synchronized (singletonObjects) {
          instance = singletonObjects.get(beanName);
          if (instance == null) {
            instance = earlySingletonObject.get(beanName);
            ObjectFactory<?> objectFactory;
            if (instance == null && (objectFactory = singletonFactories.get(beanName)) != null) {
              instance = objectFactory.getObjectInstance();
              earlySingletonObject.put(beanName, instance);
              singletonFactories.remove(beanName);
            }
          }
        }
      }
    }
    return instance;
  }

  @Override
  public boolean isContainBean(String beanName) {
    return singletonObjects.containsKey(beanName);
  }

  @Override
  public Object getBeanContainerMutex() {
    return singletonObjects;
  }

  /**
   * 注册依赖
   *
   * @param beanName bean名
   * @param dependentName 需要依赖得bean名
   */
  public void registerBeanDependency(String beanName, String dependentName) {
    synchronized (dependentBeanMap) {
      if (!dependentBeanMap
          .computeIfAbsent(beanName, k -> new LinkedHashSet<>())
          .add(dependentName)) {
        return;
      }
    }
    synchronized (beanDependentMap) {
      beanDependentMap.computeIfAbsent(dependentName, k -> new LinkedHashSet<>()).add(beanName);
    }
  }

  /**
   * 是否依赖
   *
   * @param beanName bean名
   * @param dependentName 待检查的依赖项
   * @return 是否依赖
   */
  protected boolean isDependent(String beanName, String dependentName) {
    synchronized (dependentBeanMap) {
      return isDependent(beanName, dependentName, null);
    }
  }

  /**
   * 依赖查询
   *
   * @param beanName bean名
   * @param dependentBeanName 依赖查询的bean名
   * @param alreadySeen 已经查询过的bean
   * @return 是否依赖
   */
  private boolean isDependent(String beanName, String dependentBeanName, Set<String> alreadySeen) {
    if (alreadySeen != null && alreadySeen.contains(beanName)) {
      return false;
    }
    Set<String> dependentList = dependentBeanMap.get(beanName);
    if (dependentList == null || dependentList.isEmpty()) {
      return false;
    }
    if (dependentList.contains(dependentBeanName)) {
      return true;
    }
    if (alreadySeen == null) {
      alreadySeen = new HashSet<>();
    }
    alreadySeen.add(beanName);
    for (String transitiveDependencyName : dependentList) {
      if (isDependent(transitiveDependencyName, dependentBeanName, alreadySeen)) {
        return true;
      }
    }
    return false;
  }
}
