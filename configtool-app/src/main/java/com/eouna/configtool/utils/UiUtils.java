package com.eouna.configtool.utils;

import com.eouna.configtool.ConfigToolGenApplication;
import javafx.fxml.FXMLLoader;

/**
 * 界面工具
 *
 * @author CCL
 */
public class UiUtils {

  /**
   * 获取资源下的loader
   *
   * @param windowViewName 窗口资源名 路径位于ui下的相对路径名
   * @return loader
   */
  public static FXMLLoader getFxmlLoader(String windowViewName) {
    return new FXMLLoader(
        ConfigToolGenApplication.class.getResource("ui/" + windowViewName + ".fxml"));
  }
}
