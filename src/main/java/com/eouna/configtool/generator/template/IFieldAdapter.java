package com.eouna.configtool.generator.template;

import java.util.Set;

/**
 * 字段适配器接口
 *
 * @author CCL
 */
public interface IFieldAdapter<T> {

  /**
   * 解析字符串字段为目标数据
   *
   * @param fieldType 字段字符串
   * @param fieldStr 待转换的字符数据
   * @return classType
   */
  T parseFiledStrToJavaClassType(String fieldStr, String fieldType);
  /**
   * 获取可接受的类型字符串
   *
   * @return 类型字符串数组
   */
  Set<String> getAcceptTypeStr();

  /**
   * 获取转后的目标类型字符串
   *
   * @param fieldType 字段类型
   * @return 类型字符串
   */
  String getTargetFieldTypeStr(String fieldType);

  /**
   * 获取转后的目标类型字符串
   *
   * @param fieldType 字段类型
   * @return 类型字符串
   */
  default String getTargetFieldObjTypeStr(String fieldType) {
    return getTargetFieldTypeStr(fieldType);
  }

  /**
   * 是否是基础类型
   *
   * @return 是否是基础类型
   */
  boolean isBaseType();

  /**
   * 获取默认值
   *
   * @return 获取默认值
   */
  default T getDefaultVal() {
    return null;
  }
}
