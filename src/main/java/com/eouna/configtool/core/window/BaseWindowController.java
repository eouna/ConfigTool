package com.eouna.configtool.core.window;

import java.io.IOException;

import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.LoggerUtils;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;

/**
 * 窗口控制器基类
 *
 * @author CCL
 * @date 2023/3/1
 */
public abstract class BaseWindowController implements IWindowController, IWindowViewRenderComplete {

  protected Stage stage;

  /** 界面数据加载和节点挂载完成,展示之前完成标记 */
  protected boolean isMounted;

  public BaseWindowController() {}

  /**
   * 获取fxml路径
   *
   * @return 路径
   */
  public abstract String getFxmlPath();

  protected Stage getOwner() {
    if (stage != null && stage.getOwner() instanceof Stage) {
      return (Stage) stage.getOwner();
    }
    return null;
  }

  /**
   * 获取Stage图标路径 从Icon目录下读取图片
   *
   * @return 路径
   */
  public String getStageIconPath() {
    return "";
  }

  @Override
  public void open() {
    getStage().show();
    onShow();
  }

  /** 当窗口可见时,这时元素选择才生效 */
  public void onShow() {}

  @Override
  public void close() {
    onClose();
    LoggerUtils.getLogger().info(getClass().getSimpleName() + "调用关闭逻辑");
    getStage().close();
  }

  protected void onClose() {}

  @Override
  public void destroy() {
    beforeDestroy();
  }

  @Override
  public void beforeDestroy() {}

  @Override
  public Stage initControllerStage(Parent parent, Object... initArgs) throws IOException {
    Scene scene = new Scene(parent);
    Stage newStage = new Stage();
    String stageIconPath = getStageIconPath();
    if (!StringUtils.isEmpty(stageIconPath)) {
      newStage.getIcons().add(new Image(FileUtils.getFullResourceUrl(stageIconPath)));
    }
    newStage.setTitle(getTitle());
    newStage.setScene(scene);
    onCreate(newStage);
    setStage(newStage);
    return newStage;
  }

  public String getTitle() {
    return "";
  }

  public void setStage(Stage stage) {
    this.stage = stage;
  }

  @Override
  public void onCreate(Stage stage) {}

  @Override
  public Stage getStage() {
    return stage;
  }

  @Override
  public String getWindowId() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void onMounted(Object... args) {}

  public boolean isMounted() {
    return isMounted;
  }

  public void setMounted(boolean mounted, Object... args) {
    isMounted = mounted;
    onMounted(args);
  }
}
