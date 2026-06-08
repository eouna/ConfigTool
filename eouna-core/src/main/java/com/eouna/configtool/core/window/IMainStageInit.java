package com.eouna.configtool.core.window;

import javafx.stage.Stage;

/**
 * 主舞台初始化接口
 *
 * @author CCL
 * @date 2023/6/19
 */
public interface IMainStageInit {
  /**
   * 初始化调用,主窗口打开时
   *
   * @param stage 主舞台
   */
  void init(Stage stage);
}
