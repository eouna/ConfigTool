package com.eouna.configtool.ui.controllers;

import java.io.File;
import java.util.stream.Collectors;

import com.eouna.configtool.core.window.BaseWindowController;
import com.eouna.configtool.ui.component.FileChoosePane.FileChooseChangeEvent;
import com.eouna.configtool.utils.LoggerUtils;
import javafx.event.Event;
import javafx.fxml.FXML;

/**
 * Description...
 *
 * @author CCL
 * @date 2023/9/1
 */
public class TestController extends BaseWindowController {

  @Override
  public String getFxmlPath() {
    return "Test";
  }

  @FXML
  private void onChange(FileChooseChangeEvent event) {
    LoggerUtils.getLogger()
        .info(
            "文件列表: {}",
            event.getSelectedDirList().stream()
                .map(File::getName)
                .collect(Collectors.joining(",")));
  }
}
