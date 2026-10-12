package com.eouna.configtool.ui.controllers;

import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.window.BaseWindowController;
import com.eouna.configtool.core.window.WindowManager;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.AnimationTimer;
import javafx.beans.InvalidationListener;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.effect.Glow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.commons.lang3.RandomUtils;

/**
 * 主窗口外围的跑马灯(装饰效果)。
 *
 * <p>在"关于"窗口点击作者后显示: 沿主窗口外框铺一圈自发光的小灯球, 并让亮光沿边框跑动。
 *
 * <p>实现要点:
 *
 * <ul>
 *   <li>浮层窗口直接取主窗口的外框矩形(Stage的x/y/width/height在Windows下包含标题栏与外边框),
 *       因此能严丝合缝地贴住整个窗口
 *   <li>监听主窗口位置/大小变化, 移动与缩放时自动重新贴合与重排灯球
 *   <li>灯球按边框周长均匀分布, 数量随窗口尺寸自适应
 *   <li>颜色沿边框整体向后传递(每个灯球取上一个灯球的颜色), 队首每步生成一个新的亮色,
 *       颜色不使用预置色组, 速度可通过{@link #setStepsPerSecond(double)}调整
 *   <li>由单个AnimationTimer统一驱动, 避免原实现的定时线程 + 每30ms重建颜色数组
 * </ul>
 *
 * @author CCL
 */
public class HorseRaceLampController extends BaseWindowController {

  /** 灯球半径 */
  private static final double BALL_RADIUS = 8;
  /** 灯球中心到主窗口外框的距离 */
  private static final double RING_OFFSET = 12;
  /** 灯球中心到浮层边缘的距离 */
  private static final double RING_INSET = BALL_RADIUS + 1;
  /** 浮层相对主窗口外框每边多出的尺寸 */
  private static final double OVERLAY_PADDING = RING_OFFSET + RING_INSET;
  /** 相邻灯球间距(按边框周长均匀分布) */
  private static final double BALL_SPACING = 20;
  /** 灯球透明度(自发光小球带一点透明) */
  private static final double BALL_OPACITY = 0.95;
  /** 默认速度: 每秒颜色向后传递的步数 */
  private static final double DEFAULT_STEPS_PER_SECOND = 16;
  /** 可调速度范围(步/秒) */
  private static final double MIN_STEPS_PER_SECOND = 1;
  private static final double MAX_STEPS_PER_SECOND = 30;
  /** 新颜色的取值范围: 高亮度的HSB, 保证生成的颜色偏亮 */
  private static final double MIN_SATURATION = 0.45;
  private static final double MAX_SATURATION = 0.9;
  private static final double MIN_BRIGHTNESS = 0.95;

  @FXML private Pane lampPane;

  /** 主窗口舞台, 浮层跟随它移动与缩放 */
  private Stage mainStage;

  /** 灯球池(按边框顺序), 尺寸变化时复用已有节点 */
  private final List<Circle> balls = new ArrayList<>();

  /** 当前有效灯球数量 */
  private int activeBallCount;

  /** 每个灯球当前的颜色(与balls同序), 每步整体向后移一位 */
  private final List<Color> ballColors = new ArrayList<>();

  /** 跑马灯动画 */
  private AnimationTimer animator;

  /** 上一次颜色步进的时刻(nanos), 0表示还没开始 */
  private long lastStepNanos;

  /** 当前速度(步/秒), 可通过关于窗口的滑块调整 */
  private static volatile double stepsPerSecond = DEFAULT_STEPS_PER_SECOND;

  /** 主窗口外框变化监听 */
  private final InvalidationListener mainWindowBoundsListener = observable -> layoutLamp();

  @Override
  public void onCreate(Stage stage) {
    ExcelGenWindowController mainWindowController =
        WindowManager.getInstance().getController(ExcelGenWindowController.class);
    mainStage = mainWindowController.getStage();
    // 移动/最大化/DPI缩放都会改动这些属性, 统一重新贴合主窗口
    mainStage.xProperty().addListener(mainWindowBoundsListener);
    mainStage.yProperty().addListener(mainWindowBoundsListener);
    mainStage.widthProperty().addListener(mainWindowBoundsListener);
    mainStage.heightProperty().addListener(mainWindowBoundsListener);
    stage.setResizable(false);
  }

  @Override
  public void onMounted(Object... args) {
    super.onMounted(args);
    // 注意: BaseWindowController#setMounted(false)也会回调到这里(窗口正在销毁),
    // 此时已经显示过的Stage不能再设置样式, 会抛"Cannot set style once stage has been set visible"
    if (!isMounted) {
      return;
    }
    // 控制器是容器单例: 关闭后再次打开时FXML会重新创建根节点,
    // 这里丢弃旧灯球并重新创建, 否则灯球还挂在已经被丢弃的旧根节点上, 新窗口里就看不到跑马灯
    balls.clear();
    ballColors.clear();
    lampPane.getChildren().clear();
    activeBallCount = 0;
    stage.initStyle(StageStyle.TRANSPARENT);
    stage.getScene().setFill(null);
    // 浮层必须完全透明: 主题样式里.root也会设置背景色, 这里用内联样式兜底,
    // 避免优先级竞争输给主题背景后整个浮层变成一块白板
    lampPane.setStyle("-fx-background-color: transparent;");
    // 浮层隐藏时(主窗口最小化等)停掉动画, 避免无意义的空跑
    stage
        .showingProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (Boolean.TRUE.equals(newValue)) {
                startLamp();
              } else {
                stopLamp();
              }
            });
  }

  @Override
  public void open() {
    // 先贴好主窗口再显示, 避免以默认尺寸闪一下
    layoutLamp();
    super.open();
  }

  @Override
  public void onShow() {
    super.onShow();
    layoutLamp();
    startLamp();
    LoggerUtils.getLogger()
        .info("跑马灯就绪: 灯球数 {}, 主窗口 {}x{}", activeBallCount, mainStage.getWidth(), mainStage.getHeight());
  }

  @Override
  public void beforeDestroy() {
    stopLamp();
    if (mainStage != null) {
      mainStage.xProperty().removeListener(mainWindowBoundsListener);
      mainStage.yProperty().removeListener(mainWindowBoundsListener);
      mainStage.widthProperty().removeListener(mainWindowBoundsListener);
      mainStage.heightProperty().removeListener(mainWindowBoundsListener);
    }
  }

  /** 贴合主窗口外框, 并按新尺寸重排灯球 */
  private void layoutLamp() {
    if (mainStage == null || stage == null) {
      return;
    }
    double overlayWidth = mainStage.getWidth() + OVERLAY_PADDING * 2;
    double overlayHeight = mainStage.getHeight() + OVERLAY_PADDING * 2;
    stage.setX(mainStage.getX() - OVERLAY_PADDING);
    stage.setY(mainStage.getY() - OVERLAY_PADDING);
    stage.setWidth(overlayWidth);
    stage.setHeight(overlayHeight);
    layoutBalls(overlayWidth, overlayHeight);
  }

  /**
   * 按浮层尺寸重排灯球
   *
   * @param overlayWidth 浮层宽度
   * @param overlayHeight 浮层高度
   */
  private void layoutBalls(double overlayWidth, double overlayHeight) {
    List<Point2D> positions = ringPositions(overlayWidth, overlayHeight);
    ensureBallPool(positions.size());
    for (int i = 0; i < balls.size(); i++) {
      Circle ball = balls.get(i);
      if (i < positions.size()) {
        Point2D position = positions.get(i);
        ball.setCenterX(position.getX());
        ball.setCenterY(position.getY());
        ball.setVisible(true);
      } else {
        ball.setVisible(false);
      }
    }
    activeBallCount = positions.size();
  }

  /**
   * 计算灯球在浮层内的中心坐标
   *
   * <p>按边框周长均匀分布, 从左上角开始顺时针排列: 相邻灯球间距一致,
   * 且数量随窗口尺寸自适应。
   *
   * @param overlayWidth 浮层宽度
   * @param overlayHeight 浮层高度
   * @return 灯球中心坐标列表
   */
  static List<Point2D> ringPositions(double overlayWidth, double overlayHeight) {
    double rectWidth = Math.max(1, overlayWidth - RING_INSET * 2);
    double rectHeight = Math.max(1, overlayHeight - RING_INSET * 2);
    double perimeter = 2 * (rectWidth + rectHeight);
    int ballCount = (int) Math.max(8, Math.round(perimeter / BALL_SPACING));
    double step = perimeter / ballCount;
    List<Point2D> positions = new ArrayList<>(ballCount);
    for (int i = 0; i < ballCount; i++) {
      positions.add(pointOnRing(rectWidth, rectHeight, i * step).add(RING_INSET, RING_INSET));
    }
    return positions;
  }

  /**
   * 取边框上距起点(左上角)arcLength处的坐标, 顺时针绕行
   *
   * @param rectWidth 边框宽度
   * @param rectHeight 边框高度
   * @param arcLength 沿边框的弧长
   * @return 坐标
   */
  private static Point2D pointOnRing(double rectWidth, double rectHeight, double arcLength) {
    if (arcLength < rectWidth) {
      return new Point2D(arcLength, 0);
    }
    arcLength -= rectWidth;
    if (arcLength < rectHeight) {
      return new Point2D(rectWidth, arcLength);
    }
    arcLength -= rectHeight;
    if (arcLength < rectWidth) {
      return new Point2D(rectWidth - arcLength, rectHeight);
    }
    arcLength -= rectWidth;
    return new Point2D(0, Math.max(0, rectHeight - arcLength));
  }

  /**
   * 按需扩充灯球池(只增不减, 复用已有节点)
   *
   * @param ballCount 需要的灯球数量
   */
  private void ensureBallPool(int ballCount) {
    while (balls.size() < ballCount) {
      Color color = randomBrightColor();
      Circle ball = createBall(color);
      balls.add(ball);
      ballColors.add(color);
      lampPane.getChildren().add(ball);
    }
  }

  /**
   * 创建一个自发光的小球: 亮芯向外逐渐透明形成光晕, 再叠加辉光
   *
   * @param baseColor 灯球基色
   * @return 灯球
   */
  static Circle createBall(Color color) {
    Circle ball = new Circle(BALL_RADIUS);
    ball.setFill(createBallFill(color));
    ball.setEffect(new Glow(0.9));
    ball.setOpacity(BALL_OPACITY);
    ball.setMouseTransparent(true);
    return ball;
  }

  /**
   * 用灯球颜色构造自发光填充: 白色亮芯向外过渡到本色, 再渐隐到透明
   *
   * @param color 灯球颜色
   * @return 填充
   */
  private static RadialGradient createBallFill(Color color) {
    return new RadialGradient(
        0,
        0,
        0.5,
        0.5,
        1,
        true,
        CycleMethod.NO_CYCLE,
        new Stop(0, Color.WHITE),
        new Stop(0.4, color),
        new Stop(0.78, color.deriveColor(0, 1, 1, 0.55)),
        new Stop(1, color.deriveColor(0, 1, 1, 0)));
  }

  /** 开始跑马灯动画 */
  private void startLamp() {
    if (animator != null || activeBallCount <= 0) {
      return;
    }
    lastStepNanos = 0;
    animator =
        new AnimationTimer() {
          @Override
          public void handle(long now) {
            if (lastStepNanos == 0) {
              // 第一帧先给所有灯球铺一次亮色
              lastStepNanos = now;
              resetBallColors();
              return;
            }
            if (now - lastStepNanos >= (long) (1_000_000_000d / getStepsPerSecond())) {
              // 帧率不足时每帧只补一步, 保证颜色传递的节奏稳定
              lastStepNanos = now;
              stepBallColors();
            }
          }
        };
    animator.start();
  }

  /** 停止跑马灯动画 */
  private void stopLamp() {
    if (animator != null) {
      animator.stop();
      animator = null;
    }
  }

  /**
   * 按灯球到灯头的距离设置透明度, 形成跑动的光带
   *
   * @param head 灯头位置(以灯球为单位, 支持小数)
   */
  /** 给所有灯球随机铺一次亮色 */
  private void resetBallColors() {
    for (int i = 0; i < activeBallCount; i++) {
      ballColors.set(i, randomBrightColor());
    }
    applyBallColors();
  }

  /**
   * 颜色向后传递一步: 每个灯球取上一个小球的颜色, 第一个灯球生成一个新颜色
   */
  private void stepBallColors() {
    for (int i = activeBallCount - 1; i > 0; i--) {
      ballColors.set(i, ballColors.get(i - 1));
    }
    ballColors.set(0, randomBrightColor());
    applyBallColors();
  }

  /** 把颜色刷到灯球上 */
  private void applyBallColors() {
    for (int i = 0; i < activeBallCount; i++) {
      balls.get(i).setFill(createBallFill(ballColors.get(i)));
    }
  }

  /**
   * 生成一个偏亮的随机颜色(高亮度、中高饱和度的HSB)
   *
   * @return 颜色
   */
  private static Color randomBrightColor() {
    return Color.hsb(
        RandomUtils.nextDouble(0, 360),
        RandomUtils.nextDouble(MIN_SATURATION, MAX_SATURATION),
        RandomUtils.nextDouble(MIN_BRIGHTNESS, 1));
  }

  /**
   * 当前速度(步/秒)
   *
   * @return 每秒颜色向后传递的步数
   */
  public static double getStepsPerSecond() {
    return stepsPerSecond;
  }

  /**
   * 调整速度(步/秒), 超出范围会被截断
   *
   * @param stepsPerSecond 每秒颜色向后传递的步数
   */
  public static void setStepsPerSecond(double stepsPerSecond) {
    HorseRaceLampController.stepsPerSecond =
        Math.max(MIN_STEPS_PER_SECOND, Math.min(MAX_STEPS_PER_SECOND, stepsPerSecond));
  }

  @Override
  public String getFxmlPath() {
    return "horse-race-lamp";
  }
}
