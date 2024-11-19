package com.eouna.configtool.core.boot.convert;

/**
 * 对象之间的转换接口,用于通过类型对具体的值进行转换
 *
 * @author CCL
 */
public interface IConverter {

  /**
   * 通过类型转换源数据到目标数据
   *
   * @param source 源数据
   * @param sourceDescriptor 源数据类型描述
   * @param targetDescriptor 目标数据描述
   * @return 目标值
   * @param <S> S
   * @param <T> T
   */
  <S, T> T convert(S source, ClassDescriptor sourceDescriptor, ClassDescriptor targetDescriptor);
}
