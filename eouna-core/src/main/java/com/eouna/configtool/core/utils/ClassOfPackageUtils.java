package com.eouna.configtool.core.utils;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.tools.JavaFileObject.Kind;

/**
 * 包内类工具
 *
 * @author CCL
 * @date 2023/4/13
 */
public class ClassOfPackageUtils {

  /**
   * 获取包名下所有的类
   *
   * @param packagePath 包名
   */
  public static Set<Class<?>> getClassesByPackage(ClassLoader classLoader, String packageName)
      throws IOException, ClassNotFoundException {
    Set<Class<?>> classes = new HashSet<>();
    String packageDirName = packageName.replace('.', '/');
    // 获取包内的url资源
    Enumeration<URL> enumeration = classLoader.getResources(packageDirName);
    while (enumeration.hasMoreElements()) {
      URL fileUrl = enumeration.nextElement();
      if ("file".equals(fileUrl.getProtocol())) {
        // 解析路径
        String fileUrlPathStr = URLDecoder.decode(fileUrl.getPath(), StandardCharsets.UTF_8.name());
        File fileUrlPath = new File(fileUrlPathStr);
        if (fileUrlPath.exists() && fileUrlPath.isDirectory()) {
          // 扫描路径下的所有文件
          Map<String, File> fileNameOfFile = mapFiles(fileUrlPath);
          for (Entry<String, File> fileEntry : fileNameOfFile.entrySet()) {
            // 获取带包名的类名
            String fileAbsoluteDotPath =
                fileEntry.getValue().getAbsolutePath().replace(File.separator, ".");
            int packageIndex = fileAbsoluteDotPath.indexOf(packageName);
            if (packageIndex < 0) {
              continue;
            }
            String classNameWithPackage =
                fileAbsoluteDotPath.substring(packageIndex);
            String fileSuffix =
                classNameWithPackage.substring(classNameWithPackage.lastIndexOf("."));
            // java或者class文件
            if (fileSuffix.equals(Kind.SOURCE.extension)
                || fileSuffix.equals(Kind.CLASS.extension)) {
              // 跳过内部类
              if (!fileEntry.getKey().contains("$")) {
                Class<?> aClass =
                    Class.forName(
                        classNameWithPackage.substring(0, classNameWithPackage.lastIndexOf(".")));
                classes.add(aClass);
              }
            }
          }
        }
      } else if ("jar".equals(fileUrl.getProtocol())) {
        JarFile jar;
        try {
          jar = ((JarURLConnection) fileUrl.openConnection()).getJarFile();
          Enumeration<JarEntry> entries = jar.entries();
          while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.charAt(0) == '/') {
              name = name.substring(1);
            }
            if (name.startsWith(packageDirName)) {
              int idx = name.lastIndexOf('/');
              if ((idx != -1)) {
                String entryPackageName = name.substring(0, idx).replace('/', '.');
                if (name.endsWith(".class") && !entry.isDirectory()) {
                  String className = name.substring(entryPackageName.length() + 1, name.length() - 6);
                  try {
                    classes.add(Class.forName(entryPackageName + '.' + className));
                  } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                  }
                }
              }
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return classes;
  }
  /** 递归列出目录下的所有文件 */
  private static Map<String, File> mapFiles(File dirFile) {
    Map<String, File> allFileMap = new java.util.HashMap<>(8);
    if (dirFile == null || !dirFile.isDirectory()) {
      return allFileMap;
    }
    File[] files = dirFile.listFiles();
    if (files == null) {
      return allFileMap;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        allFileMap.putAll(mapFiles(file));
      } else {
        allFileMap.put(file.getName(), file);
      }
    }
    return allFileMap;
  }
}