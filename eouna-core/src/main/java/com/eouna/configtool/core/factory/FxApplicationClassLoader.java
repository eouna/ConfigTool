package com.eouna.configtool.core.factory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import com.eouna.configtool.core.event.FxApplicationStartedEvent;
import com.eouna.configtool.core.event.FxApplicationStartingEvent;
import com.eouna.configtool.core.context.ApplicationListener;
import com.eouna.configtool.core.utils.ClassOfPackageUtils;

/**
 * 加载接口对应的接口实现类
 *
 * @author CCL
 * @date 2023/7/4
 */
public class FxApplicationClassLoader implements ApplicationListener<FxApplicationStartedEvent> {

  /** 包下面的class缓存 */
  private static final Map<ClassLoader, Map<String, Set<Class<?>>>> PACKAGE_CLASSES_CACHE =
      new ConcurrentHashMap<>();

  /** 接口对应的实现类列表 */
  private static final Map<ClassLoader, Map<Class<?>, Set<Class<?>>>> INTERFACE_IMPL_CACHE =
      new ConcurrentHashMap<>();

  public FxApplicationClassLoader() {}

  /**
   * 获取某个接口的实现类
   *
   * @param classLoader 类加载器
   * @param interfaceType 接口类
   * @param componentPathArr 扫描的包数组
   * @return 实现类
   */
  public static Set<Class<?>> getInterfaceImplClasses(
      ClassLoader classLoader, Class<?> interfaceType, String[] componentPathArr) {
    Map<Class<?>, Set<Class<?>>> classesOfClassloader;
    Set<Class<?>> componentOfClasses = new HashSet<>();
    // 判断缓存是否在缓存中有,
    if ((classesOfClassloader = INTERFACE_IMPL_CACHE.get(classLoader)) != null
        && classesOfClassloader.containsKey(interfaceType)) {
      return classesOfClassloader.get(interfaceType);
    }
    if (PACKAGE_CLASSES_CACHE.get(classLoader) != null) {
      for (String componentPath : componentPathArr) {
        if (PACKAGE_CLASSES_CACHE.get(classLoader).containsKey(componentPath)) {
          Set<Class<?>> packageOfClasses =
              PACKAGE_CLASSES_CACHE.get(classLoader).get(componentPath);
          // 将包下的类过滤并写入缓存
          cachePackageClass(classLoader, componentOfClasses, interfaceType, packageOfClasses);
        }
      }
      // 如果找到直接返回
      if (!componentOfClasses.isEmpty()) {
        return componentOfClasses;
      }
    }
    try {
      for (String componentPath : componentPathArr) {
        Set<Class<?>> packageOfClasses =
            ClassOfPackageUtils.getClassesByPackage(classLoader, componentPath);
        PACKAGE_CLASSES_CACHE
            .computeIfAbsent(classLoader, k -> new ConcurrentHashMap<>(8))
            .put(componentPath, packageOfClasses);
        // 将包下的类过滤并写入缓存
        cachePackageClass(classLoader, componentOfClasses, interfaceType, packageOfClasses);
      }
      return componentOfClasses;
    } catch (IOException e) {
      throw new IllegalArgumentException("从包 {} 中查找类资源失败", e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("扫描包时找不到对应的类", e);
    }
  }

  /** 从类列表中缓存指定类对应的实现类 */
  private static void cachePackageClass(
      ClassLoader classLoader,
      Set<Class<?>> componentOfClasses,
      Class<?> interfaceType,
      Set<Class<?>> packageOfClasses) {
    componentOfClasses.addAll(
        packageOfClasses.stream()
            .filter(aClass -> interfaceType.isAssignableFrom(aClass) && interfaceType != aClass)
            .collect(Collectors.toSet()));
    INTERFACE_IMPL_CACHE
        .computeIfAbsent(classLoader, k -> new ConcurrentHashMap<>(8))
        .computeIfAbsent(interfaceType, k -> new CopyOnWriteArraySet<>())
        .addAll(componentOfClasses);
  }

  @Override
  public void onEventHappen(FxApplicationStartedEvent event) {
    // 程序加载成功后移除缓存
    PACKAGE_CLASSES_CACHE.clear();
    INTERFACE_IMPL_CACHE.clear();
  }
}
