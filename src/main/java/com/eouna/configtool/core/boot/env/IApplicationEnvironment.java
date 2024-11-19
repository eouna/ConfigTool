package com.eouna.configtool.core.boot.env;

import com.eouna.configtool.core.boot.convert.ApplicationConverters;

/**
 * 程序环境接口
 *
 * @author CCL
 */
public interface IApplicationEnvironment {

  /**
   * 保存程序转换器的接口
   *
   * @param applicationConverters Convert
   */
  void setApplicationConvertors(ApplicationConverters applicationConverters);
}
