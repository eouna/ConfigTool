package com.eouna.configtool.ui.controllers;

import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.window.BaseWindowController;
import com.eouna.configtool.utils.FileUtils;
import java.io.File;
import java.util.Map;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

/**
 * 配置表搜索功能
 *
 * @author CCL
 * @date 2023/5/29
 */
public class ExcelSearchController extends BaseWindowController {

  /** 搜索结果展示区域 */
  @FXML TextArea searchResShowArea;

  @FXML
  private void searchExcelFieldData() {
    String excelDir =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath();
    File excelFileDir = new File(excelDir);
    if (!excelFileDir.isDirectory() || !excelFileDir.exists()) {
      return;
    }
    Map<File, String> fileMd5Map =
        FileUtils.getDirMd5CodeList(excelFileDir, FileUtils.EXCEL_FILTER);
  }

  @Override
  public String getTitle() {
    return "搜索";
  }

  @Override
  public String getFxmlPath() {
    return "excel-search";
  }
}
