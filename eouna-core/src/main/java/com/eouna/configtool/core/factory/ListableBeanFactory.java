package com.eouna.configtool.core.factory;

import java.util.List;

/**
 * Description...
 *
 * @author CCL
 * @date 2023/10/9
 */
public interface ListableBeanFactory extends IBeanFactory {

  /**
   * 根据类或者接口类型获取bean名字列表
   *
   * @param classType 类或者接口类型
   * @return bean名字列表
   */
  List<String> getBeanNamesForType(Class<?> classType);

  /**
   * 确保所有非惰性初始化单例都已实例化
   */
  void preInstantiateSingletons();

  /**
   * 获取bean的数量
   *
   * @return 数量
   */
  int getBeanListSize();
}
