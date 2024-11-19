package com.eouna.configtool.core.window;

import java.io.IOException;

import javafx.scene.Parent;
import javafx.stage.Stage;

/**
 * 窗口控制器
 *
 * @author CCL
 */
public interface IWindowController {

  /**
   * 获取窗口名
   *
   * @return 窗口名
   */
  String getWindowId();

  /**
   * 获取场景
   *
   * @return 获取场景
   */
  Stage getStage();

  /** 窗口关闭 */
  void close();

  /** 窗口展示 */
  void open();

  /** 销毁窗口之前 */
  void beforeDestroy();

  /** 销毁接口 */
  void destroy();

  /**
   * 初始化Controller
   *
   * @param parent parent
   * @param initArgs 初始化
   * @return 场景
   * @throws IOException e
   */
  Stage initControllerStage(Parent parent, Object... initArgs) throws IOException;

  /**
   * 创建时
   *
   * @param stage 创建时触发
   */
  void onCreate(Stage stage);
}
