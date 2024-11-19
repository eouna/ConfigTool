package com.eouna.configtool.core.window;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.LoggerUtils;
import com.eouna.configtool.utils.UiUtils;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * 窗口控制器容器
 *
 * @author CCL
 * @date 2023/3/1
 */
@Component
public class WindowManager {

  /** 窗口控制器容器 */
  private final Map<String, BaseWindowController> windowControllerContainer =
      new ConcurrentHashMap<>();

  /** 舞台下挂载的窗口 */
  private final Map<Stage, List<BaseWindowController>> stageOfOpenedWindowList =
      new ConcurrentHashMap<>();

  /** 窗口对应的舞台 */
  private final Map<String, Stage> windowOfStageCache = new ConcurrentHashMap<>();

  /** 开始节点 */
  private WindowNode startWindowNode;

  /** 当前已打开的节点 currentOpenedNode */
  private WindowNode currentOpenedNode;

  /**
   * 注册窗口控制器
   *
   * @param controller 控制器
   */
  public void registerController(BaseWindowController controller) {
    if (controller != null) {
      String windowId = controller.getWindowId();
      if (windowControllerContainer.containsKey(windowId)) {
        LoggerUtils.getLogger().error("窗口重复注册");
        return;
      }
      windowControllerContainer.put(windowId, controller);
      // 注册时更新当前窗口节点
      updateCurrentWindowNode(controller);
      // 添加windId与舞台的映射
      windowOfStageCache.put(controller.getWindowId(), controller.getStage());
    }
  }

  /**
   * 通过controller获取所有窗口
   *
   * @param controllerClass 窗口class
   * @return 所有窗口控制器
   */
  public List<BaseWindowController> getAllWindowControllerByClass(
      Class<? extends BaseWindowController> controllerClass) {
    if (controllerClass == null) {
      throw new IllegalArgumentException("参数为空");
    }
    List<BaseWindowController> baseWindowControllers = new ArrayList<>();
    for (BaseWindowController value : windowControllerContainer.values()) {
      if (value.getClass().isAssignableFrom(controllerClass)) {
        baseWindowControllers.add(value);
      }
    }
    return baseWindowControllers;
  }

  /**
   * 打开单窗口
   *
   * @param controllerClass 控制器class
   * @param args 用于初始化和创建时的参数
   * @return 窗口控制器
   * @param <T> 窗口控制器基类泛型
   */
  public <T extends BaseWindowController> T openWindow(Class<T> controllerClass, Object... args) {
    T baseWindowController = getOrCreateController(controllerClass, args);
    if (!baseWindowController.isMounted()) {
      baseWindowController.setMounted(true, args);
    }
    baseWindowController.open();
    baseWindowController.getStage().toFront();
    return baseWindowController;
  }

  /**
   * 打开单窗口
   *
   * @param controllerClassName 控制器class名字
   * @param args 用于初始化和创建时的参数
   * @return 窗口控制器
   * @param <T> 窗口控制器基类泛型
   */
  public <T extends BaseWindowController> T openWindow(String controllerClassName, Object... args) {
    Class<T> controllerClass;
    try {
      controllerClass = (Class<T>) Class.forName(controllerClassName);
    } catch (ClassNotFoundException e) {
      LoggerUtils.getTextareaLogger().error("找不到窗口控制器: {}", controllerClassName);
      throw new RuntimeException("找不到窗口控制器: " + controllerClassName);
    }
    // 打开新窗口
    return openWindow(controllerClass, args);
  }

  /**
   * 替换窗口
   *
   * @param controllerClassName 控制器class名
   * @param args 用于初始化和创建时的参数
   * @param isKeepCurWindowData 是否保存当前窗口数据
   * @return 窗口控制器
   * @param <T> 窗口控制器基类泛型
   */
  public <T extends BaseWindowController> T replaceWindow(
      String controllerClassName, boolean isKeepCurWindowData, Object... args) {
    // 打开新窗口
    BaseWindowController controller = openWindow(controllerClassName, args);
    // 隐藏当前窗口的上一个窗口
    closePreWindow(isKeepCurWindowData);
    return (T) controller;
  }

  /**
   * 展示指定窗口的前一个打开的窗口
   *
   * @param isReopenPreWindow 是否重新打开之前得窗口
   */
  public void openPreWindow(boolean isReopenPreWindow) {
    // 获取最近未关闭的窗口
    if (currentOpenedNode == null || currentOpenedNode.pre == null) {
      return;
    }
    BaseWindowController preWindow = currentOpenedNode.pre.windowController;
    if (preWindow == null) {
      return;
    }
    if (isReopenPreWindow) {
      if (preWindow.getStage() != null) {
        // 先销毁
        preWindow.destroy();
      }
      // 再打开
      openWindow(preWindow.getClass());
    } else {
      // 打开窗口
      preWindow.open();
    }
  }

  /**
   * 隐藏指定窗口的前一个打开的窗口
   *
   * @param isKeepPreWindowData 是否保持上一个窗口得数据,如果窗口需要保持则只隐藏窗口
   */
  public void closePreWindow(boolean isKeepPreWindowData) {
    if (currentOpenedNode != null) {
      WindowNode node = currentOpenedNode.pre;
      if (node == null) {
        return;
      }
      BaseWindowController baseWindowController = node.windowController;
      // 关闭上一个窗口
      if (baseWindowController == null) {
        return;
      }
      if (isKeepPreWindowData) {
        baseWindowController.close();
      } else {
        baseWindowController.destroy();
      }
    }
  }

  /**
   * 关闭窗口
   *
   * @param controllerClass 控制器class
   * @param <T> 窗口控制器基类泛型
   */
  public <T extends BaseWindowController> void closeWindow(Class<T> controllerClass) {
    BaseWindowController baseWindowController = getController(controllerClass);
    if (baseWindowController != null) {
      baseWindowController.close();
    }
  }

  /**
   * 通过创建ID关闭窗口
   *
   * @param windowId 窗口ID
   * @param <T> 窗口控制器基类泛型
   */
  public <T extends BaseWindowController> void closeWindow(String windowId) {
    if (windowControllerContainer.containsKey(windowId)) {
      BaseWindowController baseWindowController = windowControllerContainer.get(windowId);
      if (baseWindowController != null) {
        baseWindowController.close();
      }
    }
  }

  /**
   * 通过窗口ID销毁窗口
   *
   * @param windowId 窗口ID
   */
  private void destroyWindow(WindowEvent windowEvent, String windowId) {
    if (windowControllerContainer.containsKey(windowId)) {
      BaseWindowController baseWindowController = windowControllerContainer.get(windowId);
      // 如果只剩最后一个窗口则需要询问,是否关闭窗口
      if (isAllWindowWillDestroy(baseWindowController)) {
        boolean exitRes = applicationExitAlert();
        if (exitRes) {
          destroyWindow(baseWindowController);
          // 向系统发送程序关闭命令
          System.exit(15);
        } else {
          windowEvent.consume();
        }
      } else {
        if (baseWindowController != null) {
          baseWindowController.close();
          destroyWindow(baseWindowController);
        }
      }
    }
  }

  /**
   * 弹出关闭提醒
   *
   * @return 是否关闭成功
   */
  private boolean applicationExitAlert() {
    AtomicBoolean atomicBoolean = new AtomicBoolean();
    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setTitle("关闭提示");
    alert.setHeaderText("是否关闭程序");
    alert.setContentText("请选择");
    alert
        .showAndWait()
        .ifPresent(
            buttonType -> atomicBoolean.set(buttonType.getButtonData().equals(ButtonData.OK_DONE)));
    return atomicBoolean.get();
  }

  /**
   * 是否所有窗口都将销毁
   *
   * @param baseWindowController 窗口控制器
   * @return 是否销毁所有
   */
  private boolean isAllWindowWillDestroy(BaseWindowController baseWindowController) {
    if (windowControllerContainer.size() == 1 || windowOfStageCache.size() == 1) {
      return true;
    }
    Stage stage = windowOfStageCache.get(baseWindowController.getWindowId());
    return stageOfOpenedWindowList.containsKey(stage) && stageOfOpenedWindowList.size() == 1;
  }

  /**
   * 销毁窗口
   *
   * @param baseWindowController 控制器
   */
  public void destroyWindow(BaseWindowController baseWindowController) {
    if (baseWindowController != null) {
      String windowId = baseWindowController.getWindowId();
      WindowNode oldNode = searchWindowNode(windowId);
      if (oldNode != null) {
        // 当前关闭的节点是不是正在显示的节点,如果显示的节点,在关闭后需要重新设置当前节点
        boolean destroyWindowIsCurrentNode = oldNode == currentOpenedNode;
        WindowNode preNode = oldNode.pre;
        WindowNode nextNode = oldNode.next;
        if (nextNode != null) {
          nextNode.pre = preNode;
        }
        if (preNode != null) {
          preNode.next = nextNode;
          currentOpenedNode = destroyWindowIsCurrentNode ? preNode : currentOpenedNode;
          // 原来的窗口销毁后,需要打开最新的窗口
          if (destroyWindowIsCurrentNode) {
            openWindow(currentOpenedNode.getWindowController().getClass());
          }
        } else {
          // 如果到头节点了,需要将开始节点重置, 如果前后两个节点都为空了,则说明全部关闭了
          startWindowNode = nextNode;
          if (startWindowNode == null) {
            destroyAllWindow();
          }
        }
      }
      if (baseWindowController.getStage().isShowing()) {
        baseWindowController.close();
      }
      windowControllerContainer.remove(windowId);
      LoggerUtils.getLogger()
          .info(
              "销毁窗口: {} #ID: {}",
              baseWindowController.getTitle(),
              baseWindowController.getWindowId());
    }
  }

  /** 关闭所有窗口 */
  public void destroyAllWindow() {
    Platform.runLater(this::destroyAllWindowInFxThread);
  }

  /** 关闭所有窗口 */
  private void destroyAllWindowInFxThread() {
    Iterator<Map.Entry<String, BaseWindowController>> windowIterator =
        windowControllerContainer.entrySet().iterator();
    while (windowIterator.hasNext()) {
      BaseWindowController windowController = windowIterator.next().getValue();
      windowController.destroy();
      destroyWindow(windowController);
      windowIterator.remove();
    }
  }

  /**
   * 打开窗口通过已存在的舞台,会替换原来舞台上的窗口
   *
   * @param stage 现有的舞台
   * @param controllerClass 控制器class
   * @param args 用于初始化和创建时的参数
   * @return 窗口控制器
   * @param <T> 窗口控制器基类泛型
   */
  public <T extends BaseWindowController> T openWindowWithStage(
      Stage stage, Class<T> controllerClass, Object... args) {
    T baseWindowController = getOrCreateController(controllerClass, stage, args);
    if (!baseWindowController.isMounted()) {
      Scene scene = baseWindowController.getStage().getScene();
      stage.setTitle(baseWindowController.getTitle());
      baseWindowController.getStage().initOwner(stage);
      baseWindowController.getStage().setScene(scene);
      baseWindowController
          .getStage()
          .getIcons()
          .add(new Image(FileUtils.getFullResourceUrl(baseWindowController.getStageIconPath())));
      baseWindowController.setMounted(true, args);
      stageOfOpenedWindowList
          .computeIfAbsent(baseWindowController.getStage(), k -> new CopyOnWriteArrayList<>())
          .add(baseWindowController);
      windowOfStageCache.put(baseWindowController.getWindowId(), stage);
    }
    baseWindowController.open();
    baseWindowController.getStage().toFront();
    LoggerUtils.getLogger()
        .info(
            "通过stage打开窗口: {}-{}", controllerClass.getSimpleName(), baseWindowController.getTitle());
    return baseWindowController;
  }

  /**
   * 将节点放到最后
   *
   * @param baseWindowController 控制器
   */
  private void updateCurrentWindowNode(BaseWindowController baseWindowController) {
    // 如果为空则新打开的则为根节点
    if (startWindowNode == null) {
      startWindowNode = new WindowNode(baseWindowController);
      // 将最新打开的窗口设置为开始的节点
      currentOpenedNode = startWindowNode;
    }
    WindowNode findNode;
    // 如果找不到说明是新窗口
    if ((findNode = searchWindowNode(baseWindowController.getWindowId())) == null) {
      // 添加节点至末尾
      WindowNode oldWindowNode = currentOpenedNode;
      WindowNode newNode = new WindowNode(baseWindowController);
      oldWindowNode.next = newNode;
      newNode.pre = oldWindowNode;
      currentOpenedNode = newNode;
      LoggerUtils.getLogger()
          .info("更新当前窗口节点: {}", currentOpenedNode.getWindowController().getTitle());
    } else {
      // 将当前节点的下一个设置为空,将找到的节点设置为最新的打开的节点
      WindowNode next;
      // 并销毁其他已打开的节点
      while ((next = findNode.next) != null) {
        next.getWindowController().destroy();
      }
      if (currentOpenedNode != findNode) {
        // 原来的也需要销毁
        currentOpenedNode.getWindowController().destroy();
        currentOpenedNode = findNode;
        LoggerUtils.getLogger()
            .info("更新当前窗口节点: {}", currentOpenedNode.getWindowController().getTitle());
      }
      currentOpenedNode.next = null;
    }
    StringBuilder nodeLinkStr = new StringBuilder();
    WindowNode node = startWindowNode;
    nodeLinkStr.append(node.getWindowController().getTitle());
    while ((node = node.next) != null) {
      nodeLinkStr.append("->").append(node.getWindowController().getTitle());
    }
    LoggerUtils.getLogger().info("当前窗口节点路径: " + nodeLinkStr);
  }

  /**
   * 打开窗口通过已存在的场景
   *
   * @param scene 现有的场景
   * @param controllerClass 控制器class
   * @param args 用于初始化和创建时的参数
   * @return 窗口控制器
   * @param <T> 窗口控制器基类泛型
   */
  public <T extends BaseWindowController> T openWindowByScene(
      Class<T> controllerClass, Scene scene, Object... args) {
    T baseWindowController = getOrCreateController(controllerClass, args);
    if (!baseWindowController.isMounted()) {
      baseWindowController.getStage().setScene(scene);
      baseWindowController.setMounted(true, args);
    }
    baseWindowController.open();
    baseWindowController.getStage().toFront();
    return baseWindowController;
  }

  /**
   * 通过类获取控制器
   *
   * @param windowControllerClass class
   * @return 控制器
   */
  private <BFC extends BaseWindowController> BFC getOrCreateController(
      Class<BFC> windowControllerClass, Object... args) {
    BaseWindowController baseWindowController;
    if (windowControllerContainer.containsKey(windowControllerClass.getSimpleName())) {
      baseWindowController = windowControllerContainer.get(windowControllerClass.getSimpleName());
    } else {
      try {
        // 控制器
        baseWindowController = loadFxmlAndGetController(windowControllerClass, args);
      } catch (InstantiationException
          | IllegalAccessException
          | IOException
          | InvocationTargetException
          | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    return (BFC) baseWindowController;
  }

  /**
   * 加载fxml并注册和获取对应的controller
   *
   * @param controllerClass controller类
   * @param args 参数
   * @return controller
   * @param <BFC> 控制器
   * @throws InstantiationException 实例创建失败异常
   * @throws IllegalAccessException 非法调用异常
   * @throws IOException IO异常加载fxml时抛出
   */
  private <BFC extends BaseWindowController> BFC loadFxmlAndGetController(
      Class<BFC> controllerClass, Object... args)
      throws InstantiationException,
          IllegalAccessException,
          IOException,
          NoSuchMethodException,
          InvocationTargetException {
    BaseWindowController baseWindowController;
    // 临时控制器实例
    BaseWindowController tempControllerIns = controllerClass.getDeclaredConstructor().newInstance();
    FXMLLoader fxmlLoader = UiUtils.getFxmlLoader(tempControllerIns.getFxmlPath());
    // 加载器
    Parent parent = fxmlLoader.load();
    // 控制器
    baseWindowController = fxmlLoader.getController();
    // 创建
    Stage stage = baseWindowController.initControllerStage(parent, args);
    // 绑定舞台
    baseWindowController.setStage(stage);
    // 进行注册
    registerController(baseWindowController);
    // 添加关闭事件
    stage.setOnCloseRequest((event) -> destroyWindow(event, baseWindowController.getWindowId()));
    return (BFC) baseWindowController;
  }

  /**
   * 通过类获取控制器
   *
   * @param aClass class
   * @return 控制器
   */
  public <BFC extends BaseWindowController> BFC getController(Class<BFC> aClass) {
    if (windowControllerContainer.containsKey(aClass.getSimpleName())) {
      BaseWindowController baseWindowController =
          windowControllerContainer.get(aClass.getSimpleName());
      return (BFC) baseWindowController;
    } else {
      throw new RuntimeException("窗口尚未初始化,无法获取控制器");
    }
  }

  /**
   * 窗口是否初始化
   *
   * @param aClass 窗口class
   * @return 是否初始化
   * @param <BFC> T
   */
  public <BFC extends BaseWindowController> boolean isWindowInitialized(Class<BFC> aClass) {
    if (windowControllerContainer.containsKey(aClass.getSimpleName())) {
      return windowControllerContainer.get(aClass.getSimpleName()).isMounted();
    }
    return false;
  }

  /**
   * 是否可重复打开建口
   *
   * @param controllerClass 控制器class
   * @return 是否可重复打开
   */
  private boolean canRepeatOpen(Class<? extends BaseWindowController> controllerClass) {
    return controllerClass.isAssignableFrom(BaseMultiWindowController.class);
  }

  /**
   * 单例
   *
   * @return WindowControllerContainer
   */
  public static WindowManager getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final WindowManager instance;

    Singleton() {
      this.instance = new WindowManager();
    }

    public WindowManager getInstance() {
      return instance;
    }
  }

  private WindowNode searchWindowNode(String windowId) {
    if (startWindowNode == null) {
      return null;
    }
    WindowNode searchNode = startWindowNode;
    if (searchNode.getWindowController().getWindowId().equalsIgnoreCase(windowId)) {
      return searchNode;
    }
    boolean hasFind;
    while (!(hasFind =
        (searchNode.getWindowController().getWindowId().equalsIgnoreCase(windowId)))) {
      searchNode = searchNode.next;
      if (searchNode == null) {
        break;
      }
    }
    return hasFind ? searchNode : null;
  }

  /** 一页节点窗口 此窗口只会保持使用一页 */
  private static class WindowNode {

    /** 节点中的窗口 */
    private final BaseWindowController windowController;

    // 父节点
    private WindowNode pre;
    // 子节点
    private WindowNode next;

    public WindowNode(BaseWindowController windowController) {
      this.windowController = windowController;
    }

    public BaseWindowController getWindowController() {
      return windowController;
    }
  }
}
