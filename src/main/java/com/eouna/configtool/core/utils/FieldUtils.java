package com.eouna.configtool.core.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.eouna.configtool.template.java.BaseCfgContainer;
import com.eouna.configtool.ui.controllers.AboutController;
import com.eouna.configtool.ui.controllers.ExcelGenWindowController;

/**
 * class字段工具类
 *
 * @author CCL
 * @date 2023/7/7
 */
public class FieldUtils {

  /**configtool
   * 获取类的字段
   *
   * @param aClass class
   * @param fieldName 字段名
   * @return field
   * @throws NoSuchFieldException e
   */
  public static Field getClassField(Class<?> aClass, String fieldName) throws NoSuchFieldException {
    assert aClass != null;
    for (Class<?> acls = aClass; acls != null; acls = acls.getSuperclass()) {
      try {
        return acls.getDeclaredField(fieldName);
      } catch (final NoSuchFieldException ex) {
        // ignore
      }
    }
    throw new NoSuchFieldException(aClass.getName() + " 找不到字段: " + fieldName);
  }

  /**
   * 获取目标类第一个泛型类中的所有字段
   *
   * @return 字段列表
   */
  protected Map<String, Field> getClassTypeParamAllFields(Class<?> targetClass)
      throws ClassNotFoundException {
    ParameterizedType genericInterfaces = (ParameterizedType) targetClass.getGenericSuperclass();
    Type actualTypeArguments = genericInterfaces.getActualTypeArguments()[0];
    final Class<?> baseCfgBean = Class.forName(actualTypeArguments.getTypeName());
    final Map<String, Field> allFields = new HashMap<>(8);
    Class<?> currentClass = baseCfgBean;
    while (currentClass != null) {
      final Field[] declaredFields = currentClass.getDeclaredFields();
      for (Field declaredField : declaredFields) {
        allFields.putIfAbsent(declaredField.getName(), declaredField);
      }
      currentClass = currentClass.getSuperclass();
    }
    return allFields;
  }

  /**
   * 获取类中的某个字段的泛型类型,如果是单个对象没有泛型则返回空,如果有泛型则返回对象全部泛型类型
   *
   * @param aClass 类
   * @param fieldName 字段名
   * @return 字段的类型
   */
  public static List<Type> getObjectFieldTypeParameter(Class<?> aClass, String fieldName) {
    try {
      Field field = aClass.getDeclaredField(fieldName);
      Type type = field.getGenericType();
      if (type instanceof ParameterizedType) {
        Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
        return Arrays.stream(types).collect(Collectors.toList());
      } else {
        return new ArrayList<>();
      }
    } catch (NoSuchFieldException e) {
      throw new IllegalArgumentException("类不存在字段");
    }
  }
}
