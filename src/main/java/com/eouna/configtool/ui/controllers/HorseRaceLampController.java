package com.eouna.configtool.ui.controllers;

import com.eouna.configtool.core.window.BaseWindowController;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.utils.LoggerUtils;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.effect.Bloom;
import javafx.scene.effect.Glow;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author CCL
 */
public class HorseRaceLampController extends BaseWindowController {

  @FXML AnchorPane root;

  @FXML HBox top;
  @FXML HBox bottom;

  @FXML VBox left;
  @FXML VBox right;

  List<Circle> circleCache;

  AtomicInteger colorRunPointer = new AtomicInteger(0);

  List<Color> colorCache;

  /** 是否初始化过跑马灯 */
  boolean isInitedHorseRaceLamp = false;

  /** 跑马灯计时器 */
  private final ScheduledExecutorService schedule =
      new ScheduledThreadPoolExecutor(
          1,
          r -> {
            Thread t =
                new Thread(Thread.currentThread().getThreadGroup(), r, "HorseRaceLamp-Schedule", 0);
            if (t.isDaemon()) {
              t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
              t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
          });

  private final int circleRadius = 10;

  @Override
  public void onCreate(Stage stage) {
    ExcelGenWindowController excelGenWindowController =
        WindowManager.getInstance().getController(ExcelGenWindowController.class);
    // 将主窗口和当前窗口层级绑定
    stage.initOwner(excelGenWindowController.getStage());
    // 初始化主窗口的xy轴变化
    addMainWindowXyListener(excelGenWindowController);
  }

  @Override
  public void onMounted(Object... args) {
    super.onMounted(args);
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.getScene().setFill(null);
  }

  @Override
  public void onShow() {
    super.onShow();
    ExcelGenWindowController excelGenWindowController =
        WindowManager.getInstance().getController(ExcelGenWindowController.class);
    // 初始化和主窗口的相对位置
    stage.setX(excelGenWindowController.getStage().getX() - (circleRadius + 2));
    stage.setY(excelGenWindowController.getStage().getY() - circleRadius * 2);
    // 初始化跑马灯
    if (!isInitedHorseRaceLamp) {
      initHorseRaceLamp();
      isInitedHorseRaceLamp = true;
    }
  }

  /** 对主窗口添加位置的检测 */
  private void addMainWindowXyListener(ExcelGenWindowController excelGenWindowController) {
    Stage mainWindowStage = excelGenWindowController.getStage();
    mainWindowStage
        .xProperty()
        .addListener(
            (observableValue, oldValue, newValue) ->
                getStage().setX((double) newValue - (getCircleRadius() + 2)));
    mainWindowStage
        .yProperty()
        .addListener(
            (observable, oldValue, newValue) ->
                getStage().setY((double) newValue - getCircleRadius() * 2));
  }

  /** 初始化跑马灯位置 */
  private void initHorseRaceLamp() {
    // 根节点长宽
    double rootWidth = root.getWidth();
    double rootHeight = root.getHeight();
    // 横向和纵向圆的数量
    int hCircleNum = (int) Math.floor(rootWidth / (circleRadius * 2));
    int vCircleNum = (int) Math.floor((rootHeight - circleRadius * 2 * 2) / (circleRadius * 2));
    // 圆和圆初始时的颜色缓存
    circleCache = new ArrayList<>((hCircleNum + vCircleNum) * 2);
    colorCache = new ArrayList<>((hCircleNum + vCircleNum) * 2);
    // 向四个方向上的节点填充圆
    fillCircleToNode(hCircleNum, top.getChildren(), false);
    fillCircleToNode(vCircleNum, right.getChildren(), false);
    fillCircleToNode(hCircleNum, bottom.getChildren(), true);
    fillCircleToNode(vCircleNum, left.getChildren(), true);
    LoggerUtils.getLogger()
        .info(
            "rootW: {}, rootH: {}, wN: {}, hN: {}", rootWidth, rootHeight, hCircleNum, vCircleNum);
    // 添加定时任务
    schedule.scheduleAtFixedRate(
        () ->
            Platform.runLater(
                () -> {
                  int index = colorRunPointer.addAndGet(1);
                  for (int i = 0; i < circleCache.size(); i++) {
                    circleCache.get(i).setFill(colorCache.get((i + index) % circleCache.size()));
                  }
                  // 一圈走完刷新颜色
                  if (index >= circleCache.size()) {
                    colorRunPointer.set(0);
                    colorCache.clear();
                    for (int i = 0; i < circleCache.size(); i++) {
                      colorCache.add(randomColorCircle());
                    }
                  }
                }),
        0,
        30L,
        TimeUnit.MILLISECONDS);
  }

  /**
   * 给节点添加指定数量的圆
   *
   * @param circleNum 圆的数量
   * @param children 节点
   * @param isReversal 是否反向添加
   */
  private void fillCircleToNode(int circleNum, ObservableList<Node> children, boolean isReversal) {
    List<Circle> tempCircle = new ArrayList<>();
    List<Color> tempColor = new ArrayList<>();
    for (int i = 0; i < circleNum; i++) {
      // 随机颜色
      Color initColor = randomColorCircle();
      Circle circle = getNewCircleWithColor(initColor);
      children.add(circle);
      if (isReversal) {
        tempCircle.add(circle);
        tempColor.add(initColor);
      } else {
        circleCache.add(circle);
        colorCache.add(initColor);
      }
    }
    if (isReversal) {
      for (int i = tempCircle.size() - 1; i >= 0; i--) {
        circleCache.add(tempCircle.get(i));
        colorCache.add(tempColor.get(i));
      }
    }
  }

  /**
   * 创建一个新⚪
   *
   * @param initColor 初始颜色
   * @return 圆
   */
  private Circle getNewCircleWithColor(Color initColor) {
    Circle circle = new Circle(circleRadius, Color.KHAKI);
    circle.setStrokeType(StrokeType.INSIDE);
    circle.setFill(initColor);
    Bloom bloom = new Bloom();
    bloom.setThreshold(0.1);
    bloom.setInput(new Glow());
    circle.setEffect(bloom);
    return circle;
  }

  /**
   * 获取随机颜色
   *
   * @return 颜色
   */
  private Color randomColorCircle() {
    return Color.rgb(
        RandomUtils.nextInt(0, 256), RandomUtils.nextInt(0, 256), RandomUtils.nextInt(0, 256));
  }

  public int getCircleRadius() {
    return circleRadius;
  }

  @Override
  public void beforeDestroy() {
    schedule.shutdownNow();
  }

  @Override
  public String getFxmlPath() {
    return "horse-race-lamp";
  }
}
