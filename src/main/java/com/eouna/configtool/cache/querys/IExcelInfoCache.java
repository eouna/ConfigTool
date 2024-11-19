package com.eouna.configtool.cache.querys;

import java.util.List;

/**
 * excel信息缓存
 *
 * @author CCL
 */
public interface IExcelInfoCache<T> {

  /**
   * 存储excel数据
   *
   * @param excelInfoEntity excel信息实例
   */
  void save(T excelInfoEntity);

  /**
   * 根据路径获取excel信息列表
   *
   * @param excelPath excel文件路径
   * @return 信息列表
   */
  List<T> getExcelInfos(String excelPath);
}
