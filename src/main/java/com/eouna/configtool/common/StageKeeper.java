package com.eouna.configtool.common;

import javafx.stage.Stage;

/**
 * UI 舞台
 *
 * @author CCL
 * @date 2023/3/1
 */
public class StageKeeper {

  private final Stage stage;

  public StageKeeper(Stage stage) {
    this.stage = stage;
  }

  public Stage getStage() {
    return stage;
  }
}
