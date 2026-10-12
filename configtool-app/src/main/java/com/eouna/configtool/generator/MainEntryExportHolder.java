package com.eouna.configtool.generator;

import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.utils.FxApplicationContextHolder;

/**
 * 是否导出"主函数入口代码"。
 *
 * <p>由命令行 {@code --export_main=false} 或界面设置; 各语言生成器据此决定是否生成
 * DebugMain/Main 主入口模板。
 *
 * @author CCL
 */
@Component
public class MainEntryExportHolder {

  /** 是否导出主入口代码, 默认导出 */
  private volatile boolean exportMainEntry = true;

  public MainEntryExportHolder() {}

  /** 从容器获取实例 */
  public static MainEntryExportHolder getInstance() {
    return FxApplicationContextHolder.getContext().getBean(MainEntryExportHolder.class);
  }

  public boolean isExportMainEntry() {
    return exportMainEntry;
  }

  public void setExportMainEntry(boolean exportMainEntry) {
    this.exportMainEntry = exportMainEntry;
  }
}