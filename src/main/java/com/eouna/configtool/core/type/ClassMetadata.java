package com.eouna.configtool.core.type;

import java.util.List;

/**
 * 类元数据
 *
 * @author CCL
 */
public interface ClassMetadata {
  /**
   * 获取类名
   *
   * @return 类名
   */
  String getClassName();

  /**
   * 是否是接口
   *
   * @return 是否是接口
   */
  boolean isInterface();

  /**
   * 是否是抽象类
   *
   * @return 是否是抽象类
   */
  boolean isAbstract();

  /**
   * 是否是注解类
   *
   * @return 是否是注解类
   */
  boolean isAnnotation();

  /**
   * 是否是final修饰的类
   *
   * @return 是否是final修饰的类
   */
  boolean isFinal();

  /**
   * 是否是内部类
   *
   * @return 是否是内部类
   */
  boolean isInnerClass();

  /**
   * 获取所有泛型类型
   *
   * @return 泛型类型列表
   */
  List<Class<?>> getAllParameterTypeClasses();
}
