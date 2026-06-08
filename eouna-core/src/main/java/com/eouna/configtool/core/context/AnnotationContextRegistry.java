package com.eouna.configtool.core.context;

/**
 * context通用接口
 *
 * @author CCL
 */
public interface AnnotationContextRegistry {

  /**
   * 注册组件类
   *
   * @param componentClass 一个或者多个组件类
   */
  void register(Class<?>... componentClass);

  /**
   * 扫描包下的组件
   *
   * @param packageNames 报名
   */
  void scan(String... packageNames);
}
