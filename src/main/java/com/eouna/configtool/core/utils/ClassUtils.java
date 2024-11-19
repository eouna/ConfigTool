package com.eouna.configtool.core.utils;

import java.io.File;
import java.util.Objects;

/**
 * java类工具
 *
 * @author CCL
 * @date 2023/6/1
 */
public class ClassUtils {

  /**
   * 获取主运行类
   *
   * @return 主运行函数
   */
  public static Class<?> getMainApplicationClass() {
    try {
      StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
      for (StackTraceElement stackTraceElement : stackTrace) {
        if ("main".equals(stackTraceElement.getMethodName())) {
          return Class.forName(stackTraceElement.getClassName());
        }
      }
    } catch (ClassNotFoundException ex) {

    }
    return null;
  }

  /**
   * 根据类获取类所在的完整路径
   *
   * @param aClass 目标类
   * @return 类所在的路径
   */
  public static String getClassResourcePath(Class<?> aClass) {
    String mainClassPackageName = aClass.getPackage().getName();
    String mainClassPackagePath = mainClassPackageName.replace(".", File.separator);
    String mainClassPath = Objects.requireNonNull(aClass.getResource("")).getPath();
    mainClassPath = mainClassPath.replace("\\", File.separator);
    mainClassPath = mainClassPath.replace("/", File.separator);
    return mainClassPath.substring(0, mainClassPath.indexOf(File.separator + mainClassPackagePath));
  }

  /**
   * 获取类的完整包名
   *
   * @param aClass class
   * @return 包名
   */
  public static String getClassFullName(Class<?> aClass) {
    String mainClassPackageName = aClass.getPackage().getName();
    return mainClassPackageName + "." + aClass.getSimpleName();
  }

  /**
   * 获取一个类加载器
   *
   * @return cl
   */
  public static ClassLoader getDefaultClassLoader() {
    ClassLoader classLoader = null;
    try {
      classLoader = Thread.currentThread().getContextClassLoader();
    } catch (Throwable ignore) {
    }
    if (classLoader == null) {
      classLoader = ClassUtils.class.getClassLoader();
      if (classLoader == null) {
        try {
          classLoader = ClassLoader.getSystemClassLoader();
        } catch (Throwable ignore) {
        }
      }
    }
    return classLoader;
  }
}
