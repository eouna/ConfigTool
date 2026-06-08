package com.eouna.configtool.core.annotaion;

import java.util.Comparator;
import java.util.List;

import com.eouna.configtool.core.Ordered;

/**
 * 注解了order的比较器
 *
 * @author CCL
 * @date 2023/7/25
 */
public class OrderedComparator implements Comparator<Object> {

  /** 默认比较器 */
  private static final OrderedComparator DEFAULT_COMPARATOR = new OrderedComparator();

  public static <T> void sort(List<T> unorderedList) {
    if (unorderedList != null && !unorderedList.isEmpty()) {
      unorderedList.sort(DEFAULT_COMPARATOR);
    }
  }

  @Override
  public int compare(Object o1, Object o2) {
    int i1 = getOrder(o1);
    int i2 = getOrder(o2);
    return Integer.compare(i1, i2);
  }

  public static int getOrder(Object o) {
    Class<?> oClass = o.getClass();
    if (!oClass.isAnnotationPresent(Order.class) && !oClass.isAssignableFrom(Ordered.class)) {
      return Integer.MAX_VALUE;
    }
    if (oClass.isAnnotationPresent(Order.class)) {
      Order order = oClass.getAnnotation(Order.class);
      return order.value();
    }
    if (oClass.isAssignableFrom(Ordered.class)) {
      return ((Ordered) o).getOrder();
    }
    return Ordered.LOWEST_ORDER;
  }

  public static OrderedComparator getDefaultAnnoComparator() {
    return DEFAULT_COMPARATOR;
  }
}
