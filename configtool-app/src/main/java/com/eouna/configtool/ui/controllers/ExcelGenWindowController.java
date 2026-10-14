package com.eouna.configtool.ui.controllers;

import com.eouna.configtool.configholder.ConfigDataBean;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.constant.EExcelUpdateState;
import com.eouna.configtool.constant.ThemeConstant;
import com.eouna.configtool.core.annotaion.AutoInject;
import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.core.window.BaseWindowController;
import com.eouna.configtool.core.window.MainWindowIdentifier;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.core.window.WindowThemeManager;
import com.eouna.configtool.env.TemplateEnvContext;
import com.eouna.configtool.env.TemplateEnvResult;
import com.eouna.configtool.generator.DataLoadSourceHolder;
import com.eouna.configtool.generator.DefaultFuture;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.generator.TemplateMainEntrySupport;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.utils.*;
import com.eouna.configtool.utils.hotreload.HotClassLoaderUtils;
import com.eouna.configtool.utils.hotreload.HotClassLoaderUtils.MethodArgDataTuple;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.DateUtils;

/**
 * excel生成展示窗口
 *
 * @author CCL
 */
@MainWindowIdentifier
@Component
public class ExcelGenWindowController extends BaseWindowController {

  // region============================== textField =============================
  @FXML private TextField excelConfigPathField;
  @FXML private TextField templateGenTargetPathField;
  @FXML private TextField templatePathField;

  /** =============== excel配置 =============== */
  @FXML private TextField excelFileDescRowField;

  @FXML private TextField excelFileTypeRowField;
  @FXML private TextField excelFileNameRowField;
  @FXML private TextField excelFileDataRangeRowField;

  // endregion============================== textField ==============================
  // region============================== textarea ==============================
  @FXML private TextArea excelFileShowArea;
  @FXML private TextFlow logShowArea;

  // endregion============================== textarea ==============================
  // region============================== button ============================
  @FXML private Button excelConfigPathButton;
  @FXML private Button templateGenTargetPathButton;
  @FXML private Button templatePathButton;
  @FXML private Button generateTemplateBtn;
  @FXML private Button refreshBtn;
  @FXML private Button preloadBtn;
  @FXML private Button skipPreloadBtn;
  @FXML private Button deleteMainEntryBtn;
  @FXML private Button prevStepBtn;
  @FXML private Button nextStepBtn;
  @FXML private RadioMenuItem neumorphismThemeMenuItem;
  @FXML private RadioMenuItem lightThemeMenuItem;
  @FXML private RadioMenuItem grandBudapestThemeMenuItem;
  @FXML private Button syncConfBtn;
  // endregion============================== button ==============================
  // region============================== form =============================
  @FXML private VBox textFormBox;
  @FXML private VBox rootBox;

  // endregion============================== form ==============================
  // region============================== BOX =============================
  /** 进度条 */
  @FXML HBox currentProgress;

  /** 语言模板选择容器(单选框) */
  @FXML FlowPane templateLangPane;

  /** 语言模板选择组(单选) */
  @FXML ToggleGroup templateLangGroup;

  /** 语言加载选项选择组(单选: excel / json) */
  @FXML ToggleGroup loadSourceGroup;

  /** 加载选项: excel */
  @FXML RadioButton loadSourceExcelRadio;

  /** 加载选项: json */
  @FXML RadioButton loadSourceJsonRadio;

  /** js语言对应的单选框(只有加载选项为json时才可用) */
  @FXML RadioButton langJsRadio;

  /** 加载选项提示 */
  @FXML Label loadSourceTipLabel;

  /** 步骤内容容器(三步界面在此切换) */
  @FXML StackPane stepPaneContainer;

  /** 第一步: 生成语言模板(外层滚动) */
  @FXML ScrollPane stepGenerateScroll;

  /** 第二步: 语言预加载(外层滚动) */
  @FXML ScrollPane stepPreloadScroll;

  /** 第三步: 上传远端服务器(外层滚动) */
  @FXML ScrollPane stepUploadScroll;

  /** 第一步: 生成语言模板 */
  @FXML VBox stepGeneratePane;

  /** 第二步: 语言预加载 */
  @FXML VBox stepPreloadPane;

  /** 第三步: 上传远端服务器 */
  @FXML VBox stepUploadPane;

  /** 服务器选择器 */
  @FXML VBox serverSelector;

  // endregion============================== Box ==============================
  // region============================== label ==============================
  @FXML Label currentProgressTips;

  /** 步骤标题 */
  @FXML Label stepTitleLabel;

  /** 步骤提示 */
  @FXML Label stepHintLabel;

  /** 第二步: 当前语言 */
  @FXML Label preloadLangLabel;

  /** 第二步: 运行环境状态 */
  @FXML Label envStatusLabel;

  /** 第二步: 预加载结果 */
  @FXML Label preloadResultLabel;

  // endregion============================== label ==============================
  // region============================== ScrollPane =============================
  @FXML ScrollPane excelShowListContainer;

  /** 日志滚动条 */
  @FXML ScrollPane logShowScrollPane;

  // endregion============================== ScrollPane ==============================

  private static final String DIGITAL_REG = "\\d+";

  /** 加载选项: excel */
  private static final String LOAD_SOURCE_EXCEL = "excel";

  /** 加载选项: json */
  private static final String LOAD_SOURCE_JSON = "json";

  /** 模板文件生成按钮锁 */
  private static final AtomicBoolean TEMPLATE_IN_PROGRESS = new AtomicBoolean(true);

  /** 当前语言是否已完成预加载; 用于保持“删除主入口”按钮可见 */
  private volatile boolean preloadSucceeded = false;

  /** 第一步(生成模板)在当前选择下是否已完成; 完成前「下一步」置灰, 完成后点击「下一步」不重复生成 */
  private volatile boolean generationCompleted = false;

  /** 第一步(生成模板)是否正在执行; 执行期间禁止切换步骤 */
  private volatile boolean generationRunning = false;

  /** 第二步(预加载)是否已完成(预加载成功或用户已跳过); 完成前「下一步」置灰 */
  private volatile boolean preloadDone = false;

  /** 第二步(预加载)是否正在执行; 执行期间预加载/跳过按钮全部置灰 */
  private volatile boolean preloadRunning = false;

  /** excel选中列表 */
  private static final ObservableSet<String> EXCEL_SELECTED_LIST =
      FXCollections.synchronizedObservableSet(FXCollections.observableSet());

  /** 当前进度计数器,用于界面底部进度显示 */
  private static final AtomicInteger CURRENT_PROGRESS_COUNTER = new AtomicInteger();

  /** 当前步骤 */
  private int currentStep = STEP_GENERATE;

  /** 步骤常量: 生成语言模板 */
  private static final int STEP_GENERATE = 1;

  /** 步骤常量: 语言预加载 */
  private static final int STEP_PRELOAD = 2;

  /** 步骤常量: 上传服务器 */
  private static final int STEP_UPLOAD = 3;

  /** 在生成过程中防止窗口变长, 所以需要固定进度条的长度 */
  private double percentageWidth;

  /** 选择的服务器列表 */
  private final List<String> selectedServerList = new ArrayList<>();

  /** excel更新状态 */
  private EExcelUpdateState excelUpdateState = EExcelUpdateState.NONE;

  /** 文字域日志 */
  private TextAreaLogger textAreaLogger;

  @AutoInject private WindowThemeManager windowThemeManager;

  /** 各语言模板的运行环境检测结果 */
  @AutoInject private TemplateEnvContext templateEnvContext;

  /** 当前选择的数据读取方式(excel/json), 生成时供各语言模板选择数据源模板 */
  @AutoInject private DataLoadSourceHolder dataLoadSourceHolder;

  public TextFlow getLogShowArea() {
    return logShowArea;
  }

  public TextAreaLogger getTextAreaLogger() {
    return textAreaLogger;
  }

  public HBox getCurrentProgress() {
    return currentProgress;
  }

  public ExcelGenWindowController() {}

  // region============================== 点击事件 =============================
  @FXML
  protected void onNeumorphismThemeSelected() {
    windowThemeManager.setTheme(ThemeConstant.NEUMORPHISM);
    syncThemeMenuSelection(ThemeConstant.NEUMORPHISM);
  }

  @FXML
  protected void onLightThemeSelected() {
    windowThemeManager.setTheme(ThemeConstant.LIGHT);
    syncThemeMenuSelection(ThemeConstant.LIGHT);
  }

  @FXML
  protected void onGrandBudapestThemeSelected() {
    windowThemeManager.setTheme(ThemeConstant.GRAND_BUDAPEST);
    syncThemeMenuSelection(ThemeConstant.GRAND_BUDAPEST);
  }

  private void syncThemeMenuSelection(String themeId) {
    Platform.runLater(
        () -> {
          boolean neumorphism = ThemeConstant.NEUMORPHISM.equals(themeId);
          boolean light = ThemeConstant.LIGHT.equals(themeId);
          boolean grandBudapest = ThemeConstant.GRAND_BUDAPEST.equals(themeId);
          neumorphismThemeMenuItem.setSelected(neumorphism);
          lightThemeMenuItem.setSelected(light);
          grandBudapestThemeMenuItem.setSelected(grandBudapest);
        });
  }

  /** onclick */
  @FXML
  protected void onExcelConfigPathSelectClick() {
    chooseFileDir(
        "excel配置文件目录",
        excelConfigPathField,
        SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath());
    // 刷新一次列表
    reloadExcelList();
    SystemConfigHolder.getInstance()
        .getExcelConf()
        .getPath()
        .setExcelConfigLoadPath(excelConfigPathField.getText());
    // excel目录变化: 生成结果作废
    invalidateGeneratedResult();
  }

  @FXML
  protected void onTemplateGenTargetConfigPathSelectClick() {
    chooseFileDir(
        "生成java文件目录",
        templateGenTargetPathField,
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir());
    SystemConfigHolder.getInstance()
        .getExcelConf()
        .getPath()
        .setTemplateFileGenTargetDir(templateGenTargetPathField.getText());
    // 生成目录变化: 原生成结果与预加载结果作废
    invalidateGeneratedResult();
  }

  @FXML
  protected void onTemplatePathSelectClick() {
    chooseFileDir(
        "java模板文件目录",
        templatePathField,
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplatePath());
    SystemConfigHolder.getInstance()
        .getExcelConf()
        .getPath()
        .setTemplatePath(templatePathField.getText());
    // 模板目录变化: 生成结果作废
    invalidateGeneratedResult();
  }

  @FXML
  protected void onExcelFileDescRowFieldChange() {
    setExcelFieldData(
        excelFileDescRowField,
        (newRow) ->
            SystemConfigHolder.getInstance().getExcelConf().getFieldRows().setFieldDescRow(newRow),
        "excel字段描述行更新");
  }

  @FXML
  protected void onExcelFileTypeRowFieldChange() {
    setExcelFieldData(
        excelFileTypeRowField,
        (newRow) ->
            SystemConfigHolder.getInstance().getExcelConf().getFieldRows().setFieldTypeRow(newRow),
        "excel字段类型行更新");
  }

  @FXML
  protected void onExcelFileNameRowFieldChange() {
    setExcelFieldData(
        excelFileNameRowField,
        (newRow) ->
            SystemConfigHolder.getInstance().getExcelConf().getFieldRows().setFieldNameRow(newRow),
        "excel字段名行更新");
  }

  @FXML
  protected void onExcelFileDataRangeRowFieldChange() {
    setExcelFieldData(
        excelFileDataRangeRowField,
        (newRow) ->
            SystemConfigHolder.getInstance()
                .getExcelConf()
                .getFieldRows()
                .setFieldDataRangeRow(newRow),
        "excel字段数值范围行更新");
  }

  /** 语言模板单选变化 */
  @FXML
  protected void onTemplateLangChanged() {
    // js模板的数据源必须是json
    if (isJsLangSelected()) {
      forceLoadSourceJson();
    }
    // 语言变化: 第一步/第二步的操作结果作废, 「下一步」重新置灰
    invalidateGeneratedResult();
    refreshLangOptionUsage();
    if (currentStep == STEP_PRELOAD) {
      refreshPreloadStep();
    }
  }

  /** 语言加载选项单选变化 */
  @FXML
  protected void onLoadSourceChanged() {
    // 数据读取方式变化: 第一步/第二步的操作结果作废, 「下一步」重新置灰
    invalidateGeneratedResult();
    refreshLangOptionUsage();
    if (currentStep == STEP_PRELOAD) {
      refreshPreloadStep();
    }
  }

  /**
   * 刷新语言/加载选项的联动约束。
   *
   * <p>约束: 语言模板为js时只能选中json(js读取的是工具导出的json数据); 加载选项为excel时不可选择js。
   */
  private void refreshLangOptionUsage() {
    boolean jsLang = isJsLangSelected();
    boolean excelLoad = isExcelLoadSelected();
    if (jsLang && excelLoad) {
      // 兜底: 选中js时把加载选项切到json
      forceLoadSourceJson();
      excelLoad = false;
    }
    // js 选中时禁止选择 excel(只能 json); 其余语言两个选项都可用
    if (loadSourceExcelRadio != null) {
      loadSourceExcelRadio.setDisable(jsLang);
    }
    if (loadSourceTipLabel != null) {
      if (jsLang) {
        loadSourceTipLabel.setText("js 的数据源为 json");
      } else if (LOAD_SOURCE_JSON.equalsIgnoreCase(getSelectedLoadSource())) {
        loadSourceTipLabel.setText("选择 json 数据源时会同时导出 json 数据文件");
      } else {
        loadSourceTipLabel.setText("");
      }
    }
  }

  /** 当前选中的语言模板标识 */
  private String getSelectedLangId() {
    if (templateLangGroup == null || templateLangGroup.getSelectedToggle() == null) {
      return "java";
    }
    Object userData = templateLangGroup.getSelectedToggle().getUserData();
    return userData == null ? "java" : userData.toString();
  }

  /** 当前选中的语言生成器 */
  private ETemplateGenerator getSelectedLangGenerator() {
    return ETemplateGenerator.getTemplateGeneratorByType(getSelectedLangId());
  }

  /** 是否选中了js语言模板 */
  private boolean isJsLangSelected() {
    return ETemplateGenerator.JS_GENERATOR == getSelectedLangGenerator();
  }

  /** 当前选中的加载选项 */
  private String getSelectedLoadSource() {
    if (loadSourceGroup == null || loadSourceGroup.getSelectedToggle() == null) {
      return LOAD_SOURCE_EXCEL;
    }
    Object userData = loadSourceGroup.getSelectedToggle().getUserData();
    return userData == null ? LOAD_SOURCE_EXCEL : userData.toString();
  }

  /** 加载选项是否为excel */
  private boolean isExcelLoadSelected() {
    return LOAD_SOURCE_EXCEL.equalsIgnoreCase(getSelectedLoadSource());
  }

  /** 强制把加载选项切换为json */
  private void forceLoadSourceJson() {
    if (loadSourceGroup == null) {
      return;
    }
    for (Toggle toggle : loadSourceGroup.getToggles()) {
      Object userData = toggle.getUserData();
      if (userData != null && LOAD_SOURCE_JSON.equalsIgnoreCase(userData.toString())) {
        toggle.setSelected(true);
        return;
      }
    }
  }

  /**
   * 本次需要生成的模板集合。
   *
   * <p>语言模板为单选; 若选择js或数据读取方式选择json, 则同时生成json数据文件。
   */
  private Set<ETemplateGenerator> getSelectedGenerators() {
    Set<ETemplateGenerator> generators = new HashSet<>();
    ETemplateGenerator selected = getSelectedLangGenerator();
    generators.add(selected);
    if (selected == ETemplateGenerator.JS_GENERATOR
        || LOAD_SOURCE_JSON.equalsIgnoreCase(getSelectedLoadSource())) {
      generators.add(ETemplateGenerator.JSON_GENERATOR);
    }
    return generators;
  }

  // region============================== 步骤切换 ==============================

  /** 上一步 */
  @FXML
  protected void onPrevStepClick() {
    switchStep(currentStep - 1);
  }

  /**
   * 下一步: 仅用于步骤切换。
   *
   * <p>当前步骤的操作完成后「下一步」才可用; 若操作已完成(例如从第二步点「上一步」再点「下一步」),
   * 直接跳到下一步, 不会重复执行上一步的操作。
   */
  @FXML
  protected void onNextStepClick() {
    if (isStepOperationRunning()) {
      return;
    }
    switch (currentStep) {
      case STEP_GENERATE:
        if (generationCompleted) {
          switchStep(STEP_PRELOAD);
        } else {
          textAreaLogger.info("请先点击「生成模板」完成第一步");
        }
        break;
      case STEP_PRELOAD:
        if (preloadDone) {
          goToUploadStep();
        } else {
          textAreaLogger.info("请先点击「预加载」或「跳过」完成第二步");
        }
        break;
      default:
        break;
    }
  }

  /** 切换到指定步骤 */
  private void switchStep(int step) {
    if (step < STEP_GENERATE || step > STEP_UPLOAD) {
      return;
    }
    if (step == STEP_UPLOAD && !hasServerOption()) {
      if (textAreaLogger != null) {
        textAreaLogger.info("没有可用的服务器配置, 不进入上传步骤");
      }
      return;
    }
    currentStep = step;
    refreshStepView();
  }

  /** 刷新步骤界面(三步界面在同一个区域内切换) */
  private void refreshStepView() {
    if (stepGeneratePane == null) {
      return;
    }
    setStepVisible(stepGenerateScroll, currentStep == STEP_GENERATE);
    setStepVisible(stepPreloadScroll, currentStep == STEP_PRELOAD);
    setStepVisible(stepUploadScroll, currentStep == STEP_UPLOAD);
    if (stepTitleLabel != null) {
      stepTitleLabel.setText(getStepTitle(currentStep));
    }
    if (stepHintLabel != null) {
      stepHintLabel.setText(getStepHint(currentStep));
    }
    // 当前步骤的操作未完成/正在执行时「下一步」置灰
    refreshStepNavButtons();
    if (currentStep == STEP_PRELOAD) {
      refreshPreloadStep();
    }
    // 切换步骤后把滚动条复位到顶部(避免自动滚动到获得焦点的控件)
    Platform.runLater(
        () -> {
          ScrollPane currentStepScroll = getCurrentStepScroll();
          if (currentStepScroll != null) {
            currentStepScroll.setVvalue(0);
          }
        });
  }

  /**
   * 刷新底部导航按钮状态。
   *
   * <p>规则: 当前步骤的操作正在执行时禁止切换步骤; 当前步骤的操作未完成时「下一步」置灰。
   */
  private void refreshStepNavButtons() {
    // 生成回调可能来自工作线程, 统一回到UI线程刷新按钮
    if (!Platform.isFxApplicationThread()) {
      Platform.runLater(this::refreshStepNavButtons);
      return;
    }
    boolean running = isStepOperationRunning();
    if (prevStepBtn != null) {
      prevStepBtn.setDisable(running || currentStep <= STEP_GENERATE);
    }
    if (nextStepBtn != null) {
      nextStepBtn.setDisable(running || !isCurrentStepFinished());
    }
  }

  /** 当前步骤的操作是否正在执行 */
  private boolean isStepOperationRunning() {
    if (currentStep == STEP_GENERATE) {
      return generationRunning;
    }
    if (currentStep == STEP_PRELOAD) {
      return preloadRunning;
    }
    return false;
  }

  /** 当前步骤的操作是否已完成(完成后才允许进入下一步) */
  private boolean isCurrentStepFinished() {
    switch (currentStep) {
      case STEP_GENERATE:
        return generationCompleted;
      case STEP_PRELOAD:
        return preloadDone;
      default:
        // 第三步是最后一步, 没有下一步
        return false;
    }
  }

  /**
   * 生成相关的选择发生变化时调用: 第一步/第二步的操作结果作废, 「下一步」重新置灰。
   */
  private void invalidateGeneratedResult() {
    generationCompleted = false;
    resetPreloadState();
    refreshStepNavButtons();
  }

  /** 当前步骤对应的滚动容器 */
  private ScrollPane getCurrentStepScroll() {
    switch (currentStep) {
      case STEP_PRELOAD:
        return stepPreloadScroll;
      case STEP_UPLOAD:
        return stepUploadScroll;
      default:
        return stepGenerateScroll;
    }
  }

  /** 设置步骤面板的显示与占位 */
  private void setStepVisible(Node stepPane, boolean visible) {
    if (stepPane == null) {
      return;
    }
    stepPane.setVisible(visible);
    stepPane.setManaged(visible);
  }

  private String getStepTitle(int step) {
    switch (step) {
      case STEP_PRELOAD:
        return "第二步: 语言预加载";
      case STEP_UPLOAD:
        return "第三步: 上传远端服务器";
      default:
        return "第一步: 生成语言模板";
    }
  }

  private String getStepHint(int step) {
    switch (step) {
      case STEP_PRELOAD:
        return "点击「预加载」或「跳过」, 完成后可进入下一步";
      case STEP_UPLOAD:
        return "勾选服务器后点击「同步配置表」";
      default:
        return "选择语言后点击「生成模板」, 生成完成后可进入下一步";
    }
  }

  /** 是否存在可选的服务器 */
  private boolean hasServerOption() {
    return serverSelector != null && !serverSelector.getChildren().isEmpty();
  }

  /**
   * 刷新: 重置所有选择并回到第一步。
   *
   * <p>重置内容: 步骤回到第一步、语言模板与语言加载选项恢复默认(java + excel)、服务器勾选、excel勾选、 第二步预加载状态与进度条。
   */
  @FXML
  protected void onRefreshClick() {
    // 1. 回到第一步
    currentStep = STEP_GENERATE;
    // 2. 语言模板 + 语言加载选项恢复默认
    resetLanguageAndLoadSource();
    // 3. 第二步预加载状态复位
    resetPreloadState();
    // 4. 服务器勾选复位
    resetServerSelection();
    // 5. excel勾选与进度复位
    cleanExcelSelectedData();
    resetPercentageData();
    // 6. 流程状态复位(第一步/第二步的操作结果一并复位)
    this.excelUpdateState = EExcelUpdateState.NONE;
    generationCompleted = false;
    // 7. 重新加载excel列表
    reloadExcelList();
    // 8. 刷新界面
    refreshStepView();
    if (textAreaLogger != null) {
      textAreaLogger.info("已刷新: 所有选择已重置, 回到第一步");
    }
  }

  /** 语言模板与语言加载选项恢复默认(java + excel) */
  private void resetLanguageAndLoadSource() {
    if (templateLangGroup != null) {
      for (Toggle toggle : templateLangGroup.getToggles()) {
        Object userData = toggle.getUserData();
        if (userData != null && "java".equalsIgnoreCase(userData.toString())) {
          toggle.setSelected(true);
          break;
        }
      }
    }
    if (loadSourceGroup != null) {
      for (Toggle toggle : loadSourceGroup.getToggles()) {
        Object userData = toggle.getUserData();
        if (userData != null && LOAD_SOURCE_EXCEL.equalsIgnoreCase(userData.toString())) {
          toggle.setSelected(true);
          break;
        }
      }
    }
    refreshLangOptionUsage();
  }

  /** 第二步预加载相关状态复位 */
  private void resetPreloadState() {
    preloadSucceeded = false;
    preloadDone = false;
    preloadRunning = false;
    if (preloadLangLabel != null) {
      preloadLangLabel.setText("当前语言: -");
    }
    if (envStatusLabel != null) {
      envStatusLabel.setText("运行环境: -");
    }
    if (preloadResultLabel != null) {
      preloadResultLabel.setText("");
    }
    setDeleteMainEntryButtonVisible(false);
  }

  /** 设置“删除主入口”按钮显示状态, 显示时主动刷新第二步布局 */
  private void setDeleteMainEntryButtonVisible(boolean visible) {
    if (deleteMainEntryBtn == null) {
      return;
    }
    deleteMainEntryBtn.setManaged(visible);
    deleteMainEntryBtn.setVisible(visible);
    if (visible && stepPreloadPane != null) {
      stepPreloadPane.requestLayout();
      Platform.runLater(
          () -> {
            if (preloadSucceeded) {
              deleteMainEntryBtn.setManaged(true);
              deleteMainEntryBtn.setVisible(true);
              stepPreloadPane.requestLayout();
            }
          });
    }
  }

  /** 服务器勾选复位 */
  private void resetServerSelection() {
    selectedServerList.clear();
    if (serverSelector != null) {
      serverSelector.getChildrenUnmodifiable().stream()
          .filter(node -> node instanceof CheckBox)
          .map(node -> (CheckBox) node)
          .forEach(checkBox -> checkBox.setSelected(false));
    }
  }

  // endregion============================== 步骤切换 ==============================

  // region============================== 第二步: 语言预加载 ==============================

  /** 刷新第二步的运行环境状态 */
  private void refreshPreloadStep() {
    String langId = getSelectedLangId();
    ETemplateGenerator generator = getSelectedLangGenerator();
    if (preloadLangLabel != null) {
      preloadLangLabel.setText("当前语言: " + langId);
    }
    TemplateEnvResult envResult =
        templateEnvContext == null ? null : templateEnvContext.get(langId);
    boolean envAvailable = envResult != null && envResult.isAvailable();
    if (envStatusLabel != null) {
      if (envResult == null) {
        envStatusLabel.setText("运行环境: 未检测");
      } else if (!envResult.isEnvRequired()) {
        envStatusLabel.setText("运行环境: 该语言无需运行环境");
      } else if (envAvailable) {
        envStatusLabel.setText("运行环境: 可用 - " + envResult.getVersion());
      } else {
        envStatusLabel.setText("运行环境: 未检测到, 需要 " + envResult.getCommand());
      }
    }
    boolean canPreload = envAvailable && supportsPreload(generator);
    // 预加载执行期间「预加载」「跳过」都置灰, 执行完成后再亮起
    refreshPreloadButtons(canPreload);
    // 删除主入口按钮默认隐藏, 预加载成功后保持显示
    setDeleteMainEntryButtonVisible(preloadSucceeded);
    if (preloadResultLabel != null) {
      if (preloadSucceeded) {
        preloadResultLabel.setText("预加载完成, 可删除主入口或点击下一步");
      } else if (preloadDone) {
        preloadResultLabel.setText("已跳过预加载, 可点击下一步继续");
      } else {
        preloadResultLabel.setText(
            canPreload ? "可以执行预加载, 也可以直接跳过" : "暂无可用的预加载方式, 请点击「跳过」");
      }
    }
  }

  /**
   * 刷新第二步按钮的可用状态。
   *
   * <p>预加载执行期间「预加载」「跳过」置灰, 操作完成后重新亮起。
   */
  private void refreshPreloadButtons() {
    refreshPreloadButtons(isPreloadAvailable());
  }

  /** 是否可以执行预加载(运行环境可用且该语言支持预加载) */
  private boolean isPreloadAvailable() {
    TemplateEnvResult envResult =
        templateEnvContext == null ? null : templateEnvContext.get(getSelectedLangId());
    boolean envAvailable = envResult != null && envResult.isAvailable();
    return envAvailable && supportsPreload(getSelectedLangGenerator());
  }

  /** 按预加载可用性与执行状态刷新第二步按钮 */
  private void refreshPreloadButtons(boolean canPreload) {
    if (preloadBtn != null) {
      preloadBtn.setVisible(canPreload);
      preloadBtn.setManaged(canPreload);
      preloadBtn.setDisable(!canPreload || preloadRunning);
    }
    if (skipPreloadBtn != null) {
      skipPreloadBtn.setVisible(true);
      skipPreloadBtn.setManaged(true);
      skipPreloadBtn.setDisable(preloadRunning);
    }
    if (deleteMainEntryBtn != null) {
      deleteMainEntryBtn.setDisable(preloadRunning);
    }
  }

  /** 是否支持预加载(执行该语言生成的主入口) */
  private boolean supportsPreload(ETemplateGenerator generator) {
    return generator != null && TemplateMainEntrySupport.isPreloadSupported(getSelectedLangId());
  }

  /**
   * 本次预加载要读取的数据路径(与"语言加载选项"一致)
   *
   * @return excel目录 或 json数据目录
   */
  private String getPreloadDataPath() {
    if (isExcelLoadSelected()) {
      return SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath();
    }
    return SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
        + File.separator
        + "json";
  }

  /** 预加载: 执行当前语言生成的主入口, 读取当前选择的数据源 */
  @FXML
  protected void onPreloadClick() {
    // 预加载执行中不允许重复触发
    if (preloadRunning) {
      return;
    }
    String langId = getSelectedLangId();
    if (!TemplateMainEntrySupport.isPreloadSupported(langId)) {
      // 无需预加载: 第二步视为已完成, 由用户点击「下一步」继续
      preloadDone = true;
      if (preloadResultLabel != null) {
        preloadResultLabel.setText("该语言无需预加载, 可直接点击下一步");
      }
      textAreaLogger.info("{} 无需运行环境, 无需预加载", langId);
      refreshPreloadButtons();
      refreshStepNavButtons();
      return;
    }
    // 没有主入口说明还没生成, 先提示
    File outputDir =
        new File(
            SystemConfigHolder.getInstance()
                .getExcelConf()
                .getPath()
                .getTemplateFileGenTargetDir());
    if (TemplateMainEntrySupport.resolveMainEntryFiles(langId, outputDir).isEmpty()) {
      if (preloadResultLabel != null) {
        preloadResultLabel.setText("未找到主入口代码, 请先回到第一步生成模板");
      }
      textAreaLogger.error("未找到 {} 的主入口代码, 请先执行第一步生成模板", langId);
      return;
    }
    preloadByMainEntry(langId, outputDir);
  }

  /** 跳过预加载(第二步视为已完成, 之后从第一步进入可直接点「下一步」) */
  @FXML
  protected void onSkipPreloadClick() {
    if (preloadRunning) {
      return;
    }
    preloadDone = true;
    goToUploadStep();
  }

  /** 进入第三步(没有服务器配置时停留在第二步) */
  private void goToUploadStep() {
    if (!hasServerOption()) {
      if (preloadResultLabel != null) {
        preloadResultLabel.setText("没有可用的服务器配置, 无法进入上传步骤");
      }
      textAreaLogger.info("没有可用的服务器配置, 不进入上传步骤");
      return;
    }
    // 预加载完成或用户显式跳过后, 进入上传步骤即视为数据加载阶段已完成, 解锁同步按钮。
    updateExcelProcessBtnUsage(EExcelUpdateState.LOAD_DATA);
    switchStep(STEP_UPLOAD);
  }

  /** 执行主入口完成预加载 */
  private void preloadByMainEntry(String langId, File outputDir) {
    String dataPath = getPreloadDataPath();
    // 编译/运行前先确认工具链可用, 避免直接抛出CreateProcess存在的无法启动程序异常
    TemplateEnvResult envResult =
        templateEnvContext == null ? null : templateEnvContext.get(langId);
    if (envResult == null || (envResult.isEnvRequired() && !envResult.isAvailable())) {
      String requiredTool = envResult == null ? "未知" : envResult.getCommand();
      if (preloadResultLabel != null) {
        preloadResultLabel.setText("未检测到运行环境, 无法预加载");
      }
      textAreaLogger.error("未检测到 {} 的运行环境({}), 请安装对应工具链后再预加载", langId, requiredTool);
      return;
    }
    TemplateMainEntrySupport.PreloadPlan plan =
        TemplateMainEntrySupport.createPlan(langId, outputDir, dataPath, envResult);
    if (plan == null) {
      if (preloadResultLabel != null) {
        preloadResultLabel.setText("无法构建预加载命令, 请检查生成结果");
      }
      textAreaLogger.error("无法构建预加载命令, 已停留在当前步骤");
      return;
    }
    textAreaLogger.info("开始预加载 {} , 数据源: {}", langId, dataPath);
    textAreaLogger.info("执行: {}", plan.toDisplayString());
    // 预加载开始: 第二步的「预加载」「跳过」都置灰, 并禁止切换步骤
    startPreloadRunning();
    DefaultFuture.runAsync(
        () -> {
          boolean success = false;
          try {
            success = executePlan(plan);
          } catch (Exception e) {
            ToolsLoggerUtils.showErrorDialog("热加载失败", e);
          } finally {
            boolean result = success;
            Platform.runLater(
                () -> {
                  // 预加载结束: 按钮重新亮起(「下一步」是否可用取决于预加载结果)
                  preloadRunning = false;
                  preloadDone = result;
                  preloadSucceeded = result;
                  setDeleteMainEntryButtonVisible(result);
                  refreshPreloadButtons();
                  refreshStepNavButtons();
                  if (preloadResultLabel != null) {
                    preloadResultLabel.setText(
                        result ? "预加载完成, 可删除主入口或点击下一步" : "预加载失败, 可查看日志或点击「跳过」");
                  }
                  if (result) {
                    updateExcelProcessBtnUsage(EExcelUpdateState.LOAD_DATA);
                  }
                });
          }
        });
  }

  /** 进入预加载执行状态: 预加载/跳过按钮置灰, 禁止切换步骤 */
  private void startPreloadRunning() {
    preloadRunning = true;
    refreshPreloadButtons();
    refreshStepNavButtons();
    if (preloadResultLabel != null) {
      preloadResultLabel.setText("预加载中, 请稍候...");
    }
  }

  /** 依次执行计划中的命令, 输出写入日志 */
  private boolean executePlan(TemplateMainEntrySupport.PreloadPlan plan) throws Exception {
    for (List<String> command : plan.getCommands()) {
      Process process = getProcess(plan, command);
      byte[] outputBytes = process.getInputStream().readAllBytes();
      String output = decodeProcessOutput(outputBytes);
      if (!output.isEmpty()) {
        for (String line : output.split("\\R")) {
          if (!line.isEmpty()) {
            LoggerUtils.getLogger().info(line);
          }
        }
      }
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        String commandText = String.join(" ", command);
        String message = "命令执行失败(exit=" + exitCode + "): " + commandText;
        if (!output.isBlank()) {
          message += System.lineSeparator() + System.lineSeparator() + output.trim();
        }
        textAreaLogger.error(message);
        throw new IllegalStateException(message);
      }
    }
    textAreaLogger.success("预加载成功, 主入口代码可保留, 也可点击「删除主入口」移除");
    return true;
  }

  /**
   * 获取执行器
   *
   * @param plan 计划
   * @param command 命令
   * @return processor
   */
  private Process getProcess(TemplateMainEntrySupport.PreloadPlan plan, List<String> command) {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(plan.getWorkDir());
    processBuilder.redirectErrorStream(true);
    // 把该语言工具链所在目录放到PATH最前面, 避免用到其他版本的运行时DLL(如g++的libstdc++)
    String toolDir = getLangToolDir();
    if (toolDir != null) {
      Map<String, String> environment = processBuilder.environment();
      String pathKey = environment.containsKey("Path") ? "Path" : "PATH";
      environment.compute(
          pathKey, (k, oldPath) -> toolDir + File.pathSeparator + (oldPath == null ? "" : oldPath));
    }
    try {
      return processBuilder.start();
    } catch (IOException e) {
      // 可执行文件缺失等启动失败时转成可读提示, 避免直接抛出CreateProcess原始错误
      throw new IllegalStateException(
          "启动命令失败: "
              + command.get(0)
              + ", 请确认对应工具链已安装并加入PATH",
          e);
    }
  }

  /** 解码子进程输出: 优先UTF-8, 失败后回退系统原生编码(兼容Windows下工具链的GBK输出) */
  private static String decodeProcessOutput(byte[] outputBytes) {
    return ProcessOutputUtils.decode(outputBytes);
  }

  /** 当前语言工具链所在目录(用于修正运行时PATH) */
  private String getLangToolDir() {
    if (templateEnvContext == null) {
      return null;
    }
    TemplateEnvResult envResult = templateEnvContext.get(getSelectedLangId());
    if (envResult == null) {
      return null;
    }
    String executablePath = envResult.getExecutablePath();
    if (executablePath == null || "-".equals(executablePath)) {
      return null;
    }
    File executable = new File(executablePath);
    return executable.isFile() ? executable.getParent() : null;
  }

  /** 删除生成的主入口代码 */
  @FXML
  protected void onDeleteMainEntryClick() {
    String langId = getSelectedLangId();
    File outputDir =
        new File(
            SystemConfigHolder.getInstance()
                .getExcelConf()
                .getPath()
                .getTemplateFileGenTargetDir());
    List<File> mainEntries = TemplateMainEntrySupport.resolveMainEntryFiles(langId, outputDir);
    if (mainEntries.isEmpty()) {
      textAreaLogger.info("{} 没有可删除的主入口代码", langId);
      return;
    }
    int deleted = 0;
    for (File entry : mainEntries) {
      try {
        if (entry.delete()) {
          deleted++;
          textAreaLogger.info("已删除主入口文件: {}", entry.getPath());
        } else {
          textAreaLogger.error("删除主入口文件失败: {}", entry.getPath());
        }
      } catch (Exception e) {
        textAreaLogger.error("删除主入口文件异常: " + entry.getPath(), e);
      }
    }
    textAreaLogger.success("已删除 {} 个 {} 主入口文件", deleted, langId);
    if (deleteMainEntryBtn != null) {
      preloadSucceeded = false;
      setDeleteMainEntryButtonVisible(false);
    }
    if (preloadResultLabel != null) {
      preloadResultLabel.setText("主入口代码已删除");
    }
  }

  // endregion============================== 第二步: 语言预加载 ==============================

  @FXML
  protected void onConfigExplainClick() {
    WindowManager.getInstance().openWindowWithStage(stage, ConfigExplainController.class);
  }

  @FXML
  protected void onAboutClick() {
    WindowManager.getInstance().openWindowWithStage(stage, AboutController.class);
  }

  @FXML
  protected void onSettingClick() {
    WindowManager.getInstance().openWindowWithStage(stage, ConfigSettingController.class);
  }

  @FXML
  protected void selectedAll() {
    Collection<Label> allLabels = getAllExcelLabel().values();
    for (Label label : allLabels) {
      if (!label.getStyleClass().contains("excel-item-selected")) {
        label.getStyleClass().add("excel-item-selected");
      }
      EXCEL_SELECTED_LIST.add(label.getId());
    }
  }

  @FXML
  protected void cancelAll() {
    Collection<Label> allLabels = getAllExcelLabel().values();
    for (Label label : allLabels) {
      if (EXCEL_SELECTED_LIST.contains(label.getId())) {
        label.getStyleClass().remove("excel-item-selected");
        EXCEL_SELECTED_LIST.remove(label.getId());
      } else {
        if (!label.getStyleClass().contains("excel-item-selected")) {
          label.getStyleClass().add("excel-item-selected");
        }
        EXCEL_SELECTED_LIST.add(label.getId());
      }
    }
  }

  /** 同步excel配置文件到指定服 */
  @FXML
  protected void syncExcelConfigToServer() {
    File excelPathDir = getExcelDirPath();
    if (excelPathDir == null) {
      textAreaLogger.error("同步失败未找到配置表文件路径");
      return;
    }
    if (selectedServerList.isEmpty()) {
      Alert alert = new Alert(AlertType.WARNING);
      alert.setTitle("警告");
      alert.setContentText("请先选择服务器");
      alert.showAndWait();
      return;
    }
    // 获取文件列表
    Map<String, File> excelNameFileMap = FileUtils.listExcelFile(excelPathDir);
    List<File> syncExcelList =
        EXCEL_SELECTED_LIST.isEmpty()
            // 全量同步
            ? new ArrayList<>(excelNameFileMap.values())
            // 部分同步
            : excelNameFileMap.entrySet().stream()
                .filter(entry -> EXCEL_SELECTED_LIST.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    // 异步线程同步 不然会阻塞UI界面
    DefaultFuture.runAsync(
            () -> {
              ResourceUploader.syncExcelToServer(logShowArea, syncExcelList, selectedServerList);
              updateExcelProcessBtnUsage(EExcelUpdateState.SYNC_DATA);
            })
        .whenComplete(
            (res, throwable) -> {
              if (throwable != null) {
                ToolsLoggerUtils.showErrorDialog("同步配置表异常", throwable);
              }
            });
  }

  // endregion============================== 点击事件 ==============================

  /**
   * 设置字段值
   *
   * @param textField text field
   * @param consumer 值解析成功后的回调
   * @param desc 日志描述
   */
  protected void setExcelFieldData(TextField textField, Consumer<Integer> consumer, String desc) {
    String text = textField.getText();
    if (!StringUtils.isEmpty(text) && text.matches(DIGITAL_REG)) {
      textAreaLogger.info(desc + " 新值: " + text);
      int changedVal = Integer.parseInt(text);
      consumer.accept(changedVal);
      // 表头行列配置变化会影响生成结果, 第一步/第二步需要重新执行
      invalidateGeneratedResult();
    }
  }

  private void chooseFileDir(
      String chooseWindowTitle, TextField showTextField, String initFilePath) {
    Window mainWindow = getStage().getOwner();
    DirectoryChooser directoryChooser = new DirectoryChooser();
    directoryChooser.setTitle(chooseWindowTitle);
    // 设置初始文件位置
    if (!StringUtils.isEmpty(initFilePath)) {
      File initFileDir = new File(initFilePath);
      if (initFileDir.isDirectory() && initFileDir.exists()) {
        directoryChooser.setInitialDirectory(initFileDir);
      }
    }
    File file = directoryChooser.showDialog(mainWindow);
    if (file == null) {
      LoggerUtils.getLogger().info("取消文件选择");
    } else {
      showTextField.setText(file.getAbsolutePath());
      String logStr = "修改" + chooseWindowTitle + ": " + showTextField.getText();
      LoggerUtils.getLogger().info(logStr);
      textAreaLogger.info(logStr);
    }
  }

  @Override
  public void onMounted(Object... args) {
    this.textAreaLogger = new TextAreaLogger(logShowArea);
    // 每次追加日志后滚动到底部; 同时监听内容高度, 覆盖自动换行导致的布局变化。
    this.textAreaLogger.setCallbackAfterFresh(this::scrollLogToBottom);
    if (logShowArea != null) {
      logShowArea
          .heightProperty()
          .addListener((observable, oldHeight, newHeight) -> scrollLogToBottom());
    }
    // 让第一步内容至少铺满可视区域, 生成模板按钮所在行即可在剩余空间中垂直居中。
    if (stepGeneratePane != null && stepGenerateScroll != null) {
      stepGeneratePane.setMinHeight(stepGenerateScroll.getViewportBounds().getHeight());
      stepGenerateScroll
          .viewportBoundsProperty()
          .addListener(
              (observable, oldBounds, newBounds) ->
                  stepGeneratePane.setMinHeight(newBounds.getHeight()));
    }
    windowThemeManager.addThemeChangeListener(this::syncThemeMenuSelection);
    syncThemeMenuSelection(windowThemeManager.getCurrentThemeId());
    // 初始化配置字段
    initConfigField();
    // 初始化语言/加载选项的联动约束(默认: java + excel)
    refreshLangOptionUsage();
    // 刷新excel列表
    reloadExcelList();
    // 添加服务器选择列表数据
    initServerSelector();
    // 默认停留在第一步
    refreshStepView();
    Platform.runLater(
        () -> {
          if (stepGenerateScroll != null) {
            stepGenerateScroll.setVvalue(0);
          }
        });
    super.onMounted(args);
    // 列表变化时直接将状态置为NONE, 并作废已完成的生成结果
    EXCEL_SELECTED_LIST.addListener(
        (SetChangeListener<String>)
            change -> {
              updateExcelProcessBtnUsage(EExcelUpdateState.NONE);
              invalidateGeneratedResult();
            });
  }

  @Override
  public void onCreate(Stage stage) {
    super.onCreate(stage);
    stage.setResizable(false);
    stage
        .getScene()
        .getAccelerators()
        .put(new KeyCodeCombination(KeyCode.A, KeyCodeCombination.CONTROL_DOWN), this::selectedAll);
    stage
        .getScene()
        .getAccelerators()
        .put(
            new KeyCodeCombination(
                KeyCode.DIGIT1, KeyCodeCombination.CONTROL_DOWN, KeyCodeCombination.SHIFT_DOWN),
            this::onNeumorphismThemeSelected);
    stage
        .getScene()
        .getAccelerators()
        .put(
            new KeyCodeCombination(
                KeyCode.DIGIT2, KeyCodeCombination.CONTROL_DOWN, KeyCodeCombination.SHIFT_DOWN),
            this::onLightThemeSelected);

    stage
        .getScene()
        .getAccelerators()
        .put(
            new KeyCodeCombination(
                KeyCode.DIGIT3, KeyCodeCombination.CONTROL_DOWN, KeyCodeCombination.SHIFT_DOWN),
            this::onGrandBudapestThemeSelected);
  }

  /** 初始化配置字段 */
  private void initConfigField() {

    String excelConfigLoadPath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath();
    if (!StringUtils.isEmpty(excelConfigLoadPath)) {
      excelConfigPathField.setText(excelConfigLoadPath);
    }
    String templateGenTargetDirPath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir();
    if (!StringUtils.isEmpty(templateGenTargetDirPath)) {
      templateGenTargetPathField.setText(templateGenTargetDirPath);
    }
    String templatePath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplatePath();
    if (!StringUtils.isEmpty(templatePath)) {
      templatePathField.setText(templatePath);
    }
    int fileDescRow =
        SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldDescRow();
    if (fileDescRow >= 0) {
      excelFileDescRowField.setText(String.valueOf(fileDescRow));
    }
    int fileNameRow =
        SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldNameRow();
    if (fileNameRow >= 0) {
      excelFileNameRowField.setText(String.valueOf(fileNameRow));
    }
    int fileTypeRow =
        SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldTypeRow();
    if (fileTypeRow >= 0) {
      excelFileTypeRowField.setText(String.valueOf(fileTypeRow));
    }
    int fileDataRangeRow =
        SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldDataRangeRow();
    if (fileDataRangeRow >= 0) {
      excelFileDataRangeRowField.setText(String.valueOf(fileDataRangeRow));
    }
  }

  @FXML
  void tryLoadAndCheckGameData() {
    DefaultFuture.runAsync(
        () -> {
          ConfigDataBean.ExcelGenPathConf excelGenPathConf =
              SystemConfigHolder.getInstance().getExcelConf().getPath();
          String rootDir = FileUtils.getRootDir();
          // excel文件加载路径
          String excelLoadPath = excelGenPathConf.getExcelConfigLoadPath();
          // 生成临时路径
          String templateFileGenTargetDir = excelGenPathConf.getTemplateFileGenTargetDir();
          boolean keepBindExcelRelativePath =
              SystemConfigHolder.getInstance().getJavaTemplateConf().isKeepBindExcelRelativePath();
          // 如果不保留文件相对路径
          if (!keepBindExcelRelativePath) {
            // 将文件及文件夹下所有文件移动到新的文件路径中进行加载
            try {
              File excelTemplateDir =
                  FileUtils.getOrCreateDir(templateFileGenTargetDir + File.separator + "excelCopy");
              org.apache.commons.io.FileUtils.copyToDirectory(
                  FileUtils.listExcelFile(new File(excelLoadPath)).values(), excelTemplateDir);
              excelLoadPath = excelTemplateDir.getPath();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
          try {
            // 依赖的lib目录
            String javaLibDependOnPath = DefaultEnvConfigConstant.DIR_JAVA_LIB_HOT_RELOAD_DEPEND_ON;
            String dependOnLib = rootDir + File.separator + javaLibDependOnPath + File.separator;
            // 预加载配置表
            HotClassLoaderUtils.loadClassAndRun(
                logShowArea,
                templateFileGenTargetDir,
                templateFileGenTargetDir,
                dependOnLib,
                SystemConfigHolder.getInstance().getJavaTemplateConf().getPackageName(),
                SystemConfigHolder.getInstance().getJavaTemplateConf().getDataManagerClassName(),
                SystemConfigHolder.getInstance()
                    .getJavaTemplateConf()
                    .getDataManagerLoadDataCaller(),
                new MethodArgDataTuple<>(String.class, excelLoadPath));
            textAreaLogger.success("配置表加载成功!");
            // 切换状态
            updateExcelProcessBtnUsage(EExcelUpdateState.LOAD_DATA);
          } catch (Exception e) {
            if (e.getCause() instanceof InvocationTargetException) {
              ToolsLoggerUtils.showErrorDialog("数据热加载失败", e.getCause());
            } else {
              ToolsLoggerUtils.showErrorDialog("数据热加载失败", e);
            }
          } finally {
            if (!keepBindExcelRelativePath) {
              try {
                org.apache.commons.io.FileUtils.cleanDirectory(new File(excelLoadPath));
                LoggerUtils.getLogger().info("清除临时excel文件夹路径: " + excelLoadPath);
              } catch (IOException e) {
                textAreaLogger.error("预加载结束刪除文件夹: " + excelLoadPath + "失败", e);
              }
            }
          }
        });
  }

  @FXML
  void generateTemplateFiles() {
    // 防止连点出现的异常问题
    if (TEMPLATE_IN_PROGRESS.compareAndSet(true, false)) {
      File excelPathDir = getExcelDirPath();
      if (excelPathDir == null) {
        ToolsLoggerUtils.showErrorDialog("生成时发生异常", "找不到目录：" + excelConfigPathField.getText());
        return;
      }
      // 设置为不可用
      toggleTemplateGenBtn(true);
      // 设置进度条长度
      resetPercentageData();
      // 生成前先刷新一遍
      Map<String, File> afterReloadExcelNameMap = FileUtils.listExcelFile(excelPathDir);
      if (afterReloadExcelNameMap.isEmpty()) {
        ToolsLoggerUtils.showErrorDialog(
            "生成时发生异常", "目录下: " + excelPathDir.getPath() + " 未找到excel文件");
        return;
      }
      // 交由UI线程处理避免某些操作引起数组ObservableList越界
      Platform.runLater(() -> generateByFileMap(afterReloadExcelNameMap));
    }
  }

  /**
   * 通过excel文件调用生成逻辑
   *
   * @param excelFileMap excelMap
   */
  private void generateByFileMap(Map<String, File> excelFileMap) {
    try {
      boolean isWaitExcelAllLoad = true;
      final int excelSize = excelFileMap.size();
      long waitTime = System.currentTimeMillis() + 5 * DateUtils.MILLIS_PER_SECOND;
      // 等待所有excel加载完成或者超过等待时间
      while (isWaitExcelAllLoad) {
        isWaitExcelAllLoad = getAllExcelLabel().size() != excelSize;
        if (isWaitExcelAllLoad) {
          isWaitExcelAllLoad = waitTime > System.currentTimeMillis();
        }
      }
      // 如果小于等待时间
      if (waitTime > System.currentTimeMillis()) {
        // 需要等待UI线程将虚拟节点渲染完成
        textAreaLogger.info("excel列表全部渲染完成, 开始准备生成模板文件");
        // 看是否有单选文件
        Collection<File> reloadExcelFile;
        if (EXCEL_SELECTED_LIST.isEmpty()) {
          reloadExcelFile = excelFileMap.values();
          excelFileMap.forEach((k, v) -> EXCEL_SELECTED_LIST.add(k));
        } else {
          reloadExcelFile =
              EXCEL_SELECTED_LIST.stream().map(excelFileMap::get).collect(Collectors.toList());
        }
        // 将文件排序
        reloadExcelFile =
            reloadExcelFile.stream()
                .sorted(Comparator.comparing(File::getName))
                .collect(Collectors.toList());
        // 本次选中的语言模板(单选, 选择js时同时生成json)
        Set<ETemplateGenerator> selectedGenerators = getSelectedGenerators();
        // 记录本次选择的数据读取方式, 各语言模板据此选择 excel/json 数据源模板
        dataLoadSourceHolder.setLoadSource(getSelectedLoadSource());
        textAreaLogger.info("本次数据读取方式: {}", dataLoadSourceHolder.getLoadSource());
        textAreaLogger.info("本次导出语言: {}", selectedGenerators);
        // excel文件生成java文件 并行处理
        ExcelTemplateGenUtils.generateByTemplateParallel(
            textAreaLogger,
            reloadExcelFile,
            selectedGenerators,
            (res) -> {
              // 重置按钮状态
              toggleTemplateGenBtn(false);
              // 改变生成状态
              TEMPLATE_IN_PROGRESS.compareAndSet(false, true);
              Platform.runLater(
                  () -> {
                    if (res) {
                      // 切换状态
                      updateExcelProcessBtnUsage(EExcelUpdateState.GEN_TEMPLATE);
                      currentProgress.setPrefWidth(percentageWidth);
                      currentProgressTips.setText("100%");
                      // 第一步操作完成: 再次点击「下一步」直接进入第二步, 不重复生成
                      generationCompleted = true;
                      // 生成结果已更新, 第二步需要重新执行一次(预加载/跳过)
                      resetPreloadState();
                      switchStep(STEP_PRELOAD);
                    } else {
                      generationCompleted = false;
                      currentProgress.getStyleClass().remove("progress-fill-danger");
                      currentProgress.getStyleClass().add("progress-fill-danger");
                      refreshStepNavButtons();
                    }
                  });
            });
      }
    } catch (Exception e) {
      ToolsLoggerUtils.showErrorDialog("生成配置表异常", ExceptionUtils.getStackTrace(e));
    } finally {
      toggleTemplateGenBtn(false);
      TEMPLATE_IN_PROGRESS.compareAndSet(false, true);
    }
  }

  private void toggleTemplateGenBtn(boolean isClose) {
    generationRunning = isClose;
    // 生成期间只锁步骤内容区, 底部固定操作栏(清除日志/刷新)仍可用
    if (stepPaneContainer != null) {
      stepPaneContainer.setDisable(isClose);
    }
    excelShowListContainer.setDisable(isClose);
    // 生成期间「上一步」「下一步」置灰
    refreshStepNavButtons();
  }

  private void initServerSelector() {
    String serverListStr = SystemConfigHolder.getInstance().getSyncConfig().getServerList();
    String[] serverArr = serverListStr.split(",");
    for (String serverName : serverArr) {
      CheckBox checkBox = new CheckBox();
      checkBox.setText(serverName);
      checkBox.setOnAction(
          (ae) -> {
            CheckBox target = (CheckBox) ae.getTarget();
            String serverStr =
                target.getText().substring(target.getText().indexOf("(") + 1).replace(")", "");
            if (target.isSelected()) {
              selectedServerList.add(serverStr);
            } else {
              selectedServerList.remove(serverStr);
            }
          });
      serverSelector.getChildren().add(checkBox);
    }
  }

  @FXML
  private Map<String, File> reloadExcelList() {
    AnchorPane anchorPane = getExcelShowPanel();
    excelShowListContainer.setContent(anchorPane);
    textAreaLogger.info("开始查找配置表......");
    // 获取excel文件路径
    File excelPathDir = getExcelDirPath();
    if (excelPathDir == null) {
      return Collections.emptyMap();
    }
    Map<String, File> excelNameFileMap = FileUtils.listExcelFile(excelPathDir);
    if (excelNameFileMap.isEmpty()) {
      textAreaLogger.error("目录: " + excelPathDir + " 下的excel为空");
      return Collections.emptyMap();
    }
    boolean isFullDivision =
        excelNameFileMap.size() % DefaultEnvConfigConstant.EXCEL_NAME_VERTICAL_SHOW_SIZE == 0;
    int colNum =
        excelNameFileMap.size() / DefaultEnvConfigConstant.EXCEL_NAME_VERTICAL_SHOW_SIZE
            + (isFullDivision ? 0 : 1);
    HBox listViews = new HBox();
    listViews.setPrefHeight(excelShowListContainer.getPrefHeight() - 20);
    // 对文件名进行排序
    List<String> excelNameList =
        excelNameFileMap.keySet().stream()
            .sorted(Comparator.comparing(String::toLowerCase))
            .collect(Collectors.toList());
    for (int col = 0; col < colNum; col++) {
      VBox listView = getExcelVerticalShowView();
      for (int row = 0; row < DefaultEnvConfigConstant.EXCEL_NAME_VERTICAL_SHOW_SIZE; row++) {
        int idx = col * DefaultEnvConfigConstant.EXCEL_NAME_VERTICAL_SHOW_SIZE + row;
        if (idx < excelNameFileMap.size()) {
          String excelName = excelNameList.get(idx);
          Label excelShowLabel = getExcelShowLabel(excelName);
          listView.getChildren().add(excelShowLabel);
        }
      }
      listViews.getChildren().add(listView);
    }
    anchorPane.getChildren().addAll(listViews);
    // 重置excel选择的数据
    cleanExcelSelectedData();
    textAreaLogger.info("查找配置表结束,文件数量总计: " + excelNameFileMap.size());
    return excelNameFileMap;
  }

  private AnchorPane getExcelShowPanel() {
    AnchorPane anchorPane = new AnchorPane();
    anchorPane.setOnScroll(
        (e) -> {
          int strength = 4;
          double hValue = (e.getDeltaY() * strength) / excelShowListContainer.getWidth();
          double originHValue = excelShowListContainer.getHvalue();
          double targetHValue = originHValue + (-1 * hValue);
          double finalValue = Math.min(Math.max(0, targetHValue), 1);
          KeyValue keyValue = new KeyValue(excelShowListContainer.hvalueProperty(), finalValue);
          KeyFrame keyFrame = new KeyFrame(new Duration(200), keyValue);
          Timeline timeline = new Timeline(keyFrame);
          timeline.play();
        });
    anchorPane.setMinWidth(rootBox.getPrefWidth());
    anchorPane.getStyleClass().add("excel-grid");
    return anchorPane;
  }

  /** 重置进度数据 */
  public void resetPercentageData() {
    CURRENT_PROGRESS_COUNTER.set(0);
    int tipsOccurWidth = (int) currentProgressTips.getWidth();
    percentageWidth = rootBox.getWidth() - tipsOccurWidth;
    currentProgress.setPrefWidth(0);
    currentProgressTips.setText("");
    // 重置进度条颜色
    currentProgress.getStyleClass().remove("progress-fill-danger");
  }

  /**
   * 修改excel标签名
   *
   * @param excelName excel名
   * @param isLoadSuccess 是否加载成功
   */
  public void updateExcelProgress(String excelName, boolean isLoadSuccess) {
    Label label = getExcelLabelById(excelName);
    if (label != null) {
      label.getStyleClass().remove("excel-item-success");
      label.getStyleClass().remove("excel-item-danger");
      label.getStyleClass().add(isLoadSuccess ? "excel-item-success" : "excel-item-danger");
      if (isLoadSuccess) {
        int addUnit = (int) Math.floor(percentageWidth / EXCEL_SELECTED_LIST.size());
        int nextWidth =
            Math.min(CURRENT_PROGRESS_COUNTER.addAndGet(addUnit), (int) percentageWidth);
        currentProgress.setPrefWidth(nextWidth);
        // 刷新进度条
        int percentage = (int) (CURRENT_PROGRESS_COUNTER.get() / percentageWidth * 100);
        currentProgressTips.setText(Math.min(100, percentage) + "%");
      }
    } else {
      textAreaLogger.warn("查找label失败, excelName: " + excelName);
    }
  }

  /** 重置excel选择的数据 */
  private void cleanExcelSelectedData() {
    CURRENT_PROGRESS_COUNTER.set(0);
    EXCEL_SELECTED_LIST.clear();
    currentProgress.setPrefWidth(0);
    currentProgressTips.setText("");
  }

  @FXML
  private void clearLogArea() {
    this.logShowArea.getChildren().clear();
  }

  /**
   * 获取excel路径
   *
   * @return 路径文件
   */
  private File getExcelDirPath() {
    String excelConfigPathFieldText = excelConfigPathField.getText();
    File excelPathDir;
    if (StringUtils.isEmpty(excelConfigPathFieldText)
        || !(excelPathDir = new File(excelConfigPathFieldText)).exists()) {
      return null;
    }
    return excelPathDir;
  }

  private VBox getExcelVerticalShowView() {
    VBox vBox = new VBox();
    vBox.getStyleClass().add("excel-column");
    vBox.setSpacing(3);
    return vBox;
  }

  private Label getExcelShowLabel(String excelName) {
    Label label = new Label();
    label.setId(excelName);
    label.setText(excelName.split("\\.")[0]);
    label.setWrapText(true);
    label.getStyleClass().add("excel-item");
    label.setOnMouseClicked(
        event -> {
          if (EXCEL_SELECTED_LIST.contains(label.getId())) {
            EXCEL_SELECTED_LIST.remove(label.getId());
          } else {
            EXCEL_SELECTED_LIST.add(label.getId());
          }
          if (EXCEL_SELECTED_LIST.contains(label.getId())) {
            if (!label.getStyleClass().contains("excel-item-selected")) {
              label.getStyleClass().add("excel-item-selected");
            }
          } else {
            label.getStyleClass().remove("excel-item-selected");
          }
        });
    label.setPrefHeight(20);
    Font font = new Font(14);
    label.setFont(font);
    Tooltip tooltip = new Tooltip();
    tooltip.setText(excelName);
    tooltip.setGraphic(new ImageView(new Image(FileUtils.getFullResourceUrl("img/excel.png"))));
    label.setTooltip(tooltip);
    return label;
  }

  /**
   * 通过ID获取label
   *
   * @param labelId 标签ID
   * @return label标签
   */
  private Label getExcelLabelById(String labelId) {
    Map<String, Label> childrenLabel = getAllExcelLabel();
    return childrenLabel.getOrDefault(labelId, null);
  }

  /**
   * 获取所有已加载的excel标签
   *
   * @return 标签map
   */
  private Map<String, Label> getAllExcelLabel() {
    AnchorPane anchorPane = (AnchorPane) excelShowListContainer.getContent();
    ObservableList<Node> observableList = anchorPane.getChildrenUnmodifiable();
    return observableList.stream()
        .map(node -> ((HBox) node).getChildren())
        // 必须表明类型 否则aspectj不能通过编译
        .collect(ArrayList<Node>::new, ArrayList<Node>::addAll, ArrayList<Node>::addAll)
        .stream()
        .map(node -> ((VBox) node).getChildren())
        .collect(ArrayList<Object>::new, ArrayList<Object>::addAll, ArrayList<Object>::addAll)
        .stream()
        .filter(Objects::nonNull)
        .filter((label) -> ((Label) label).isManaged())
        .collect(Collectors.toMap(o -> ((Label) o).getId(), o -> (Label) o));
  }

  /** 更新excel流程相关的按钮的使用 */
  private void updateExcelProcessBtnUsage(EExcelUpdateState excelUpdateState) {
    if (this.excelUpdateState.isCanGoNext(excelUpdateState)) {
      this.excelUpdateState = excelUpdateState;
    } else {
      // 分步骤执行后允许跨步骤(例如跳过预加载直接上传), 这里不再抛异常, 仅记录状态流转
      LoggerUtils.getLogger()
          .info("excel流程状态流转: {} -> {}", this.excelUpdateState.name(), excelUpdateState.name());
      this.excelUpdateState = excelUpdateState;
    }
    generateTemplateBtn.setDisable(
        this.excelUpdateState.checkDisable(EExcelUpdateState.GEN_TEMPLATE));
    preloadBtn.setDisable(this.excelUpdateState.checkDisable(EExcelUpdateState.LOAD_DATA));
    syncConfBtn.setDisable(this.excelUpdateState.checkDisable(EExcelUpdateState.SYNC_DATA));
  }

  public ScrollPane getLogShowScrollPane() {
    return logShowScrollPane;
  }

  /** 将日志区域滚动到最底部(等待一次布局, 确保新增内容已经计入滚动高度) */
  private void scrollLogToBottom() {
    if (logShowScrollPane == null) {
      return;
    }
    logShowScrollPane.setVvalue(1.0D);
    Platform.runLater(() -> logShowScrollPane.setVvalue(1.0D));
  }

  @Override
  public String getFxmlPath() {
    return "main-view";
  }

  @Override
  public String getStageIconPath() {
    return "icon/main.png";
  }

  @Override
  public Stage getStage() {
    return stage;
  }

  @Override
  public String getTitle() {
    return "配置表生成工具 " + FileUtils.getAppVersion();
  }
}
