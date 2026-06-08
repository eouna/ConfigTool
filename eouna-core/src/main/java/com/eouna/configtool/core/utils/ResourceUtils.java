package com.eouna.configtool.core.utils;

import java.util.Objects;

import javafx.fxml.FXMLLoader;

/**
 * 资源解析工具。用于在核心模块中解析由应用模块提供的资源(fxml、icon 等)。
 *
 * <p>JPMS 下模块资源是相互隔离的,核心模块无法直接读取应用模块内的资源, 因此需要在应用启动时将应用主类注册进来, 以应用主类所在包为基准解析资源路径。
 *
 * @author CCL
 */
public final class ResourceUtils {

  /** 应用主类, 资源以其所在包为基准进行解析 */
  private static Class<?> resourceBaseClass;

  private ResourceUtils() {}

  public static void setResourceBaseClass(Class<?> resourceBaseClass) {
    ResourceUtils.resourceBaseClass = resourceBaseClass;
  }

  public static Class<?> getResourceBaseClass() {
    return resourceBaseClass;
  }

  private static Class<?> requireBaseClass() {
    Class<?> base = resourceBaseClass;
    if (base == null) {
      throw new IllegalStateException("资源基准类未设置, 请确认已通过 FxApplicationLoader 启动应用");
    }
    return base;
  }

  /** 获取资源路径(URL 形式) */
  public static String getFullResourceUrl(String resourcePath) {
    return Objects.requireNonNull(requireBaseClass().getResource(resourcePath)).toExternalForm();
  }

  /** 获取资源路径(文件路径形式) */
  public static String getFullResourcePath(String resourcePath) {
    return Objects.requireNonNull(requireBaseClass().getResource(resourcePath)).getPath();
  }

  /** 获取资源下的 fxml loader */
  public static FXMLLoader getFxmlLoader(String windowViewName) {
    return new FXMLLoader(requireBaseClass().getResource("ui/" + windowViewName + ".fxml"));
  }
}