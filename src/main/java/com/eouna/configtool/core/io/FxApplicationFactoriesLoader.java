package com.eouna.configtool.core.io;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.eouna.configtool.constant.StrConstant;
import com.eouna.configtool.core.annotaion.OrderedComparator;
import com.eouna.configtool.core.utils.BeanUtils;

/**
 * 容器内部工厂加载机制，通过配置的fxapplication.factories文件中的内容，定义某个类需要加载或者初始化的实例<br>
 *
 * @author CCL
 * @date 2023/7/25
 */
public class FxApplicationFactoriesLoader {

  /** 工厂文件路径 */
  private static final String FACTORIES_FILE_PATH = "META-INF/fxApplication.factories";

  private static final Map<ClassLoader, Map<String, List<String>>> CACHE =
      new ConcurrentHashMap<>();

  public static <T> List<T> loadFactories(Class<T> factoryType, ClassLoader classLoader) {
    Objects.requireNonNull(factoryType, "工厂类型不能为空");
    ClassLoader classLoaderToUse = classLoader;
    if (classLoaderToUse == null) {
      classLoaderToUse = FxApplicationFactoriesLoader.class.getClassLoader();
    }
    List<String> factoryImplementationNames = loadFactoryNames(factoryType, classLoaderToUse);
    List<T> result = new ArrayList<>(factoryImplementationNames.size());
    for (String factoryImplementationName : factoryImplementationNames) {
      result.add(BeanUtils.instantiateFactory(factoryImplementationName, factoryType));
    }
    OrderedComparator.sort(result);
    return result;
  }

  public static <T> List<String> loadFactoryNames(Class<T> factoryType, ClassLoader classLoader) {
    List<String> factoryNameList = new ArrayList<>();
    String factoryName = factoryType.getName();
    if (CACHE.containsKey(classLoader)) {
      factoryNameList.addAll(
          CACHE.get(classLoader).getOrDefault(factoryType.getName(), new ArrayList<>()));
      if (!factoryNameList.isEmpty()) {
        return factoryNameList;
      }
    }
    Enumeration<URL> urls;
    try {
      urls =
          (classLoader != null
              ? classLoader.getResources(FACTORIES_FILE_PATH)
              : ClassLoader.getSystemResources(FACTORIES_FILE_PATH));
      while (urls.hasMoreElements()) {
        URL url = urls.nextElement();
        Properties properties = new Properties();
        properties.load(url.openStream());
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
          String factoryTypeName = ((String) entry.getKey()).trim();
          if (factoryTypeName.equalsIgnoreCase(factoryName)) {
            for (String factoryImplementationName :
                ((String) entry.getValue()).split(StrConstant.COMMA)) {
              factoryNameList.add(factoryImplementationName.trim());
            }
            CACHE
                .computeIfAbsent(classLoader, k -> new HashMap<>(8))
                .put(factoryTypeName, factoryNameList);
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return factoryNameList;
  }
}
