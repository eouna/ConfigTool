package com.eouna.configtool.utils;

import javafx.application.HostServices;
import javafx.scene.control.Hyperlink;

/**
 * 节点工具
 *
 * @author CCL
 * @date 2023/11/24
 */
public class NodeUtils {

  public static HostServices hostServices;

  /**
   * 创建跳转链接
   *
   * @param url url
   * @return 链接节点
   */
  public static Hyperlink createJumpLink(String url) {
    Hyperlink hyperlink = new Hyperlink(url);
    hyperlink.setStyle("-fx-text-fill: green");
    hyperlink.setOnAction((e) -> hostServices.showDocument(url));
    return hyperlink;
  }
}
