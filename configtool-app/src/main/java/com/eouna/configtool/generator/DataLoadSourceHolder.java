package com.eouna.configtool.generator;

import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.utils.FxApplicationContextHolder;

/**
 * 当前选择的数据读取方式(excel / json)。
 *
 * <p>由界面「数据加载选项」设置, 生成模板时读取: 各语言模板会按该选项选择对应的数据源读取模板
 * (例如 {@code XxxDataSourceExcel.ftl} / {@code XxxDataSourceJson.ftl})。
 *
 * <p>本类通过 {@code @Component} 注册到容器统一管理; 因模板生成器是枚举单例(非容器bean), 这里提供
 * {@link #getInstance()} 从容器取实例, 与 {@code SystemConfigHolder} 一致。
 *
 * @author CCL
 */
@Component
public class DataLoadSourceHolder {

  /** 数据源: excel */
  public static final String SOURCE_EXCEL = "excel";

  /** 数据源: json */
  public static final String SOURCE_JSON = "json";

  /** 当前选择的数据源 */
  private volatile String loadSource = SOURCE_EXCEL;

  public DataLoadSourceHolder() {}

  /** 从容器中获取实例(模板生成器使用) */
  public static DataLoadSourceHolder getInstance() {
    return FxApplicationContextHolder.getContext().getBean(DataLoadSourceHolder.class);
  }

  public String getLoadSource() {
    return loadSource;
  }

  public void setLoadSource(String loadSource) {
    this.loadSource = SOURCE_JSON.equalsIgnoreCase(loadSource) ? SOURCE_JSON : SOURCE_EXCEL;
  }

  /** 是否读取json数据 */
  public boolean isJsonSource() {
    return SOURCE_JSON.equals(loadSource);
  }

  /** 是否读取excel数据 */
  public boolean isExcelSource() {
    return !isJsonSource();
  }
}