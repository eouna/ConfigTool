package com.eouna.configtool.utils.caller;

/**
 * 接口调用
 *
 * @author CCL
 * @date 2023/2/15
 */
public interface IInterfaceCallEachAction<T> {

  /**
   * 调用方法之前
   *
   * @param classEntity 实例
   */
  default void callBefore(T classEntity) {}

  /**
   * 调用实际的方法
   *
   * @param classEntity 实例
   * @throws Exception e
   */
  void call(T classEntity) throws Exception;

  /**
   * 调用方法之后
   *
   * @param classEntity 实例
   */
  default void afterCall(T classEntity) {}
}
