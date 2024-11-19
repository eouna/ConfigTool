package com.eouna.configtool.core.utils;
/**
 * 系统相关的工具方法
 *
 * @author CCL
 * @date 2023/7/17
 */
public class OsUtils {

  public static boolean isLinux() {
    return System.getProperty("os.name").startsWith("Linux");
  }

  public static boolean isWindows() {
    return System.getProperty("os.name").startsWith("Windows");
  }

  public static boolean isMacOs() {
    return System.getProperty("os.name").startsWith("Mac");
  }
}
