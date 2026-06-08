package com.eouna.configtool.core.utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;

import com.eouna.configtool.core.exceptions.InitConstructException;

/**
 * 反射工具
 *
 * @author CCL
 * @date 2023/6/19
 */
public class BeanUtils {

  /**
   * 根据参数个数查找类的指定构造函数
   *
   * @param tClass 目标类
   * @param args 构造函数的参数列表
   * @return 目标实例
   * @param <T> 实例
   */
  public static <T> T getClassInstance(Class<?> tClass, Object... args)
      throws InitConstructException {
    if (tClass.isInterface()) {
      throw new InitConstructException(tClass, "Specified class is an interface");
    }
    if (args.length == 0) {
      try {
        return (T) tClass.getDeclaredConstructor().newInstance();
      } catch (InstantiationException exception) {
        throw new InitConstructException(tClass, "抽象类不能实例化", exception);
      } catch (IllegalAccessException exception) {
        throw new InitConstructException(tClass, "构造函数是否是公共方法", exception);
      } catch (IllegalArgumentException exception) {
        throw new InitConstructException(tClass, "构造函数参数错误", exception);
      } catch (InvocationTargetException exception) {
        throw new InitConstructException(tClass, "构造函数内部逻辑异常", exception.getTargetException());
      } catch (NoSuchMethodException exception) {
        throw new InitConstructException(tClass, "找不到无参构造函数", exception);
      }
    } else {
      Constructor<?>[] declaredConstructors = tClass.getDeclaredConstructors();
      for (Constructor<?> declaredConstructor : declaredConstructors) {
        if (declaredConstructor.getParameterCount() == args.length) {
          Parameter[] parameters = declaredConstructor.getParameters();
          for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].getParameterizedType().equals(args[i].getClass())) {
              break;
            }
          }
          try {
            return (T) declaredConstructor.newInstance(args);
          } catch (IllegalAccessException exception) {
            throw new InitConstructException(tClass, "构造函数是否是公共方法", exception);
          } catch (IllegalArgumentException exception) {
            throw new InitConstructException(tClass, "构造函数参数错误", exception);
          } catch (InvocationTargetException exception) {
            throw new InitConstructException(tClass, "构造函数内部逻辑异常", exception.getTargetException());
          } catch (InstantiationException exception) {
            throw new InitConstructException(tClass, "抽象类不能实例化", exception);
          }
        }
      }
    }
    throw new InitConstructException(tClass, "初始化实例失败");
  }

  /**
   * 通过类名进行实例化
   *
   * @param factoryImplementationName 工厂实现名
   * @param factoryType 工厂名
   * @return 工厂实例
   * @param <T> T
   */
  public static <T> T instantiateFactory(
      String factoryImplementationName, Class<T> factoryType, Class<?>... parameterTypes) {
    try {
      Class<?> factoryImplementationClass = Class.forName(factoryImplementationName);
      if (!factoryType.isAssignableFrom(factoryImplementationClass)) {
        throw new IllegalArgumentException(
            "配置的类名 [" + factoryImplementationName + "] 不能分配给工厂 [" + factoryType.getName() + "]");
      }
      Constructor<T> ctor =
          (Constructor<T>) factoryImplementationClass.getDeclaredConstructor(parameterTypes);
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (Throwable ex) {
      throw new IllegalArgumentException(
          "无法实例化 [" + factoryImplementationName + "] 工厂类型为 [" + factoryType.getName() + "]", ex);
    }
  }
}
