package com.eouna.configtool.utils.caller;

import com.eouna.configtool.core.annotaion.Order;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

import com.eouna.configtool.core.annotaion.Order;

/**
 * 默认的方法比较器
 *
 * @author CCL
 * @date 2023/2/16
 */
public class DefaultMethodCallPriorityComparator<T> implements Comparator<Class<T>> {

  private final String methodName;

  public DefaultMethodCallPriorityComparator(String methodName) {
    this.methodName = methodName;
  }

  @Override
  public int compare(Class<T> o1, Class<T> o2) {
    Method method;
    method =
        Arrays.stream(o1.getMethods())
            .filter(method1 -> method1.getName().equals(methodName))
            .findFirst()
            .get();
    Order order1 = method.getAnnotation(Order.class);
    method =
        Arrays.stream(o2.getMethods())
            .filter(method1 -> method1.getName().equals(methodName))
            .findFirst()
            .get();
    Order order2 = method.getAnnotation(Order.class);
    if (order1 == null && order2 == null) {
      return 0;
    }
    if (order1 != null && order2 == null) {
      return -1;
    }
    if (order1 == null) {
      return -1;
    }
    return Integer.compare(order1.value(), order2.value());
  }
}
