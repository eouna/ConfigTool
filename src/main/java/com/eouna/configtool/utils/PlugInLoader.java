package com.eouna.configtool.utils;

import com.eouna.configtool.core.logger.LoggerUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.tools.JavaFileObject.Kind;

/**
 * 插件加载,动态加载jar文件
 *
 * @author CCL
 * @date 2023/12/4
 */
public class PlugInLoader {

  /**
   * 加载所有jar文件
   *
   * @param path 文件
   */
  public static void loadAllJarClass(File path) {
    if (!path.exists() || !path.isDirectory()) {
      return;
    }
    Map<String, File> jarFileMap =
        FileUtils.mapFiles(path, (jarPath) -> jarPath.getName().endsWith(".jar"));
    Map<File, List<String>> jarClassesMap = new HashMap<>(8);
    for (File value : jarFileMap.values()) {
      try (JarFile jarFile = new JarFile(value)) {
        Enumeration<JarEntry> jarEntryEnumeration = jarFile.entries();
        while (jarEntryEnumeration.hasMoreElements()) {
          JarEntry jarEntry = jarEntryEnumeration.nextElement();
          String jarEntryName = jarEntry.getName();
          if (jarEntryName.endsWith(Kind.CLASS.extension)) {
            jarClassesMap
                .computeIfAbsent(value, k -> new ArrayList<>())
                .add(
                    jarEntryName.substring(
                        0, jarEntryName.length() - Kind.CLASS.extension.length()));
          }
        }
      } catch (IOException ioException) {
        LoggerUtils.getLogger().info("读取jar文件: {} 时发生异常", value, ioException);
      }
    }
    URL[] urls =
        jarFileMap.values().stream()
            .map(
                file -> {
                  try {
                    return file.toURI().toURL();
                  } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(URL[]::new);
    try (URLClassLoader urlClassLoader =
        new URLClassLoader(urls, ClassLoader.getSystemClassLoader())) {
      for (Entry<File, List<String>> classNameEntry : jarClassesMap.entrySet()) {
        for (String classPathName : classNameEntry.getValue()) {
          String className = classPathName.replace("/", ".");
          try {
            Class<?> aClass = Class.forName(className, true, urlClassLoader);
            LoggerUtils.getLogger()
                .info(
                    "加载jar包: {} 中的class: {} 成功", classNameEntry.getKey().getPath(), classPathName);
          } catch (ClassNotFoundException e) {
            LoggerUtils.getLogger()
                .error(
                    "加载jar包: {} 中的classPath: {} className: {} 失败",
                    classNameEntry.getKey().getPath(),
                    classPathName,
                    className,
                    e);
            throw new RuntimeException(e);
          } catch (NoClassDefFoundError error) {
            LoggerUtils.getLogger()
                .error(
                    "找不到类定义: {} jar包: {} 中的classPath: {}",
                    className,
                    classNameEntry.getKey().getPath(),
                    classPathName,
                    error);
          }
        }
      }
    } catch (IOException ioException) {
      LoggerUtils.getLogger().error("加载Url文件错误", ioException);
    }
  }
}
