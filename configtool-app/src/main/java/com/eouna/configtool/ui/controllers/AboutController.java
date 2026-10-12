package com.eouna.configtool.ui.controllers;

import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.window.BaseWindowController;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.utils.FileUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.stage.Stage;

import java.util.Calendar;

/**
 * 关于
 *
 * @author CCL
 */
public class AboutController extends BaseWindowController {

  @FXML protected Label versionId;
  @FXML protected Label authorName;

  @FXML protected Label powerName;
  @FXML protected Label copyRightName;

  /** 跑马灯速度滑块 */
  @FXML protected Slider lampSpeedSlider;
  @FXML protected Label lampSpeedValue;

  /** 已绑定监听的滑块(重新挂载时FXML会生成新节点, 需要重新绑定) */
  private Slider lampSpeedBoundSlider;

  /** 点击作者: 显示/关闭主窗口外围的跑马灯 */
  @FXML
  public void onAuthorClicked() {
    WindowManager windowManager = WindowManager.getInstance();
    // 已经显示时再次点击则关闭, 方便随时收掉这个装饰效果
    HorseRaceLampController lamp =
        windowManager.isWindowInitialized(HorseRaceLampController.class)
            ? windowManager.getController(HorseRaceLampController.class)
            : null;
    if (lamp != null && lamp.getStage() != null && lamp.getStage().isShowing()) {
      windowManager.destroyWindow(lamp);
      return;
    }
    windowManager.openWindowWithStage(getOwner(), HorseRaceLampController.class);
  }

  @Override
  public void onCreate(Stage stage) {
    stage.setResizable(false);
  }

  @Override
  public void onMounted(Object... args) {
    super.onMounted(args);
    this.versionId.setText(FileUtils.getAppVersion());
    this.authorName.setText(DefaultEnvConfigConstant.AUTHOR);
    this.powerName.setText("Powered By " + DefaultEnvConfigConstant.COM_NAME + " Company");
    this.copyRightName.setText(
        "Copyright © 2018–"
            + Calendar.getInstance().get(Calendar.YEAR)
            + " "
            + DefaultEnvConfigConstant.COM_NAME);
    initLampSpeedControl();
  }

  /** 初始化跑马灯速度滑块: 显示当前速度, 拖动即时生效 */
  private void initLampSpeedControl() {
    if (lampSpeedSlider == null) {
      return;
    }
    lampSpeedSlider.setValue(HorseRaceLampController.getStepsPerSecond());
    refreshLampSpeedText(lampSpeedSlider.getValue());
    if (lampSpeedBoundSlider != lampSpeedSlider) {
      lampSpeedBoundSlider = lampSpeedSlider;
      lampSpeedSlider
          .valueProperty()
          .addListener(
              (observable, oldValue, newValue) -> {
                HorseRaceLampController.setStepsPerSecond(newValue.doubleValue());
                refreshLampSpeedText(newValue.doubleValue());
              });
    }
  }

  /** 刷新速度文字 */
  private void refreshLampSpeedText(double value) {
    if (lampSpeedValue != null) {
      lampSpeedValue.setText((int) Math.round(value) + " 步/秒");
    }
  }

  @Override
  public String getFxmlPath() {
    return "about";
  }

  @Override
  public String getTitle() {
    return "关于";
  }

  @Override
  public String getStageIconPath() {
    return "icon/about.png";
  }
}
