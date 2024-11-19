package com.eouna.configtool.cache.impl.sqlite;

import java.util.List;

import com.eouna.configtool.cache.entities.ExcelCacheInfo;
import com.eouna.configtool.cache.querys.IExcelInfoCache;

/**
 * excel文件缓存信息查询实现方法
 *
 * @author CCL
 * @date 2023/5/29
 */
public class ExcelInfoCacheImpl implements IExcelInfoCache<ExcelCacheInfo> {

  @Override
  public void save(ExcelCacheInfo excelInfoEntity) {

  }

  @Override
  public List<ExcelCacheInfo> getExcelInfos(String excelPath) {
    return null;
  }
}
