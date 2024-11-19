package com.eouna.configtool.ui.controllers;

import com.eouna.configtool.configholder.ConfigDataBean;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.window.MainWindowIdentifier;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.core.window.BaseWindowController;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.generator.DefaultFuture;
import com.eouna.configtool.constant.EExcelUpdateState;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.constant.DefaultEnvConfigConstant.ColorDefine;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.utils.ExcelUploader;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.HotClassLoaderUtils;
import com.eouna.configtool.utils.HotClassLoaderUtils.MethodArgDataTuple;
import com.eouna.configtool.utils.LoggerUtils;
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
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
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
  @FXML private Button preLoadBtn;
  @FXML private Button syncConfBtn;
  // endregion============================== button ==============================
  // region============================== form =============================
  @FXML private VBox textFormBox;
  @FXML private VBox rootBox;

  // endregion============================== form ==============================
  // region============================== BOX =============================
  /** 进度条 */
  @FXML HBox currentProgress;

  /** 选择器 后续添加其他语言模板只需在此容器下添加即可,并绑定相对应的与语言处理器 */
  @FXML HBox templateCheckBoxGroup;

  /** 服务器选择器 */
  @FXML VBox serverSelector;

  // endregion============================== Box ==============================
  // region============================== label ==============================
  @FXML Label currentProgressTips;
  // endregion============================== label ==============================
  // region============================== ScrollPane =============================
  @FXML ScrollPane excelShowListContainer;

  /** 日志滚动条 */
  @FXML ScrollPane logShowScrollPane;

  // endregion============================== ScrollPane ==============================

  private static final String DIGITAL_REG = "\\d+";

  /** 模板文件生成按钮锁 */
  private static final AtomicBoolean TEMPLATE_IN_PROGRESS = new AtomicBoolean(true);

  /** excel选中列表 */
  private static final ObservableSet<String> EXCEL_SELECTED_LIST =
      FXCollections.synchronizedObservableSet(FXCollections.observableSet());

  /** 当前进度计数器,用于界面底部进度显示 */
  private static final AtomicInteger CURRENT_PROGRESS_COUNTER = new AtomicInteger();

  /** 选择的模板生成器 */
  private static final Set<ETemplateGenerator> SELECTED_TEMPLATE_GENERATOR = new HashSet<>();

  /** 在生成过程中防止窗口变长, 所以需要固定进度条的长度 */
  private double percentageWidth;

  /** 选择的服务器列表 */
  private final List<String> selectedServerList = new ArrayList<>();

  /** excel更新状态 */
  private EExcelUpdateState excelUpdateState = EExcelUpdateState.NONE;

  public TextFlow getLogShowArea() {
    return logShowArea;
  }

  public HBox getCurrentProgress() {
    return currentProgress;
  }

  public ExcelGenWindowController() {}

  // region============================== 点击事件 =============================
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

  @FXML
  protected void onTemplateCheckBoxClicked(ActionEvent actionEvent) {
    CheckBox checkBox = (CheckBox) actionEvent.getSource();
    ETemplateGenerator eTemplateGenerator =
        ETemplateGenerator.getTemplateGeneratorByType(checkBox.getText());
    if (!SELECTED_TEMPLATE_GENERATOR.contains(eTemplateGenerator)) {
      SELECTED_TEMPLATE_GENERATOR.add(eTemplateGenerator);
      checkBox.setSelected(true);
    } else if (SELECTED_TEMPLATE_GENERATOR.size() > 1) {
      SELECTED_TEMPLATE_GENERATOR.remove(eTemplateGenerator);
      checkBox.setSelected(false);
    } else {
      checkBox.setSelected(true);
    }
  }

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
      label.setStyle("-fx-border-width: 1;-fx-border-color: #F8AB3799;-fx-border-radius: 3;");
      EXCEL_SELECTED_LIST.add(label.getId());
    }
  }

  @FXML
  protected void cancelAll() {
    Collection<Label> allLabels = getAllExcelLabel().values();
    for (Label label : allLabels) {
      if (EXCEL_SELECTED_LIST.contains(label.getId())) {
        label.setStyle("-fx-border-width: 1;-fx-border-color: #EEE;-fx-border-radius: 3;");
        EXCEL_SELECTED_LIST.remove(label.getId());
      } else {
        label.setStyle("-fx-border-width: 1;-fx-border-color: #F8AB3799;-fx-border-radius: 3;");
        EXCEL_SELECTED_LIST.add(label.getId());
      }
    }
  }

  /** 同步excel配置文件到指定服 */
  @FXML
  protected void syncExcelConfigToServer() {
    File excelPathDir = getExcelDirPath();
    if (excelPathDir == null) {
      LoggerUtils.getTextareaLogger().error("同步失败未找到配置表文件路径");
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
              ExcelUploader.syncExcelToServer(syncExcelList, selectedServerList);
              updateExcelProcessBtnUsage(EExcelUpdateState.SYNC_DATA);
            })
        .whenComplete(
            (res, throwable) -> {
              if (throwable != null) {
                LoggerUtils.showErrorDialog("同步配置表异常", throwable);
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
      LoggerUtils.getTextareaLogger().info(desc + " 新值: " + text);
      int changedVal = Integer.parseInt(text);
      consumer.accept(changedVal);
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
      LoggerUtils.getTextareaLogger().info(logStr);
    }
  }

  @Override
  public void onMounted(Object... args) {
    // 初始化textarea区域
    LoggerUtils.getInstance().initLogComponent(logShowArea);
    // 初始化配置字段
    initConfigField();
    // 初始化添加默认的模板
    this.templateCheckBoxGroup
        .getChildrenUnmodifiable()
        .forEach(
            (checkBox) -> {
              if (((CheckBox) checkBox).isSelected()) {
                ETemplateGenerator eTemplateGenerator =
                    ETemplateGenerator.getTemplateGeneratorByType(((CheckBox) checkBox).getText());
                SELECTED_TEMPLATE_GENERATOR.add(eTemplateGenerator);
              }
            });
    // 刷新excel列表
    reloadExcelList();
    // 添加服务器选择列表数据
    initServerSelector();
    super.onMounted(args);
    // 列表变化时直接将状态置为NONE
    EXCEL_SELECTED_LIST.addListener(
        (SetChangeListener<String>) change -> updateExcelProcessBtnUsage(EExcelUpdateState.NONE));
  }

  @Override
  public void onCreate(Stage stage) {
    super.onCreate(stage);
    stage.setResizable(false);
    stage
        .getScene()
        .getAccelerators()
        .put(new KeyCodeCombination(KeyCode.A, KeyCodeCombination.CONTROL_DOWN), this::selectedAll);
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
                templateFileGenTargetDir,
                templateFileGenTargetDir,
                dependOnLib,
                SystemConfigHolder.getInstance().getJavaTemplateConf().getPackageName(),
                SystemConfigHolder.getInstance().getJavaTemplateConf().getDataManagerClassName(),
                SystemConfigHolder.getInstance()
                    .getJavaTemplateConf()
                    .getDataManagerLoadDataCaller(),
                new MethodArgDataTuple<>(String.class, excelLoadPath));
            LoggerUtils.getTextareaLogger().info("配置表加载成功!");
            // 切换状态
            updateExcelProcessBtnUsage(EExcelUpdateState.LOAD_DATA);
          } catch (Exception e) {
            if (e.getCause() instanceof InvocationTargetException) {
              LoggerUtils.showErrorDialog("数据热加载失败", e.getCause());
            } else {
              LoggerUtils.showErrorDialog("数据热加载失败", e);
            }
          } finally {
            if (!keepBindExcelRelativePath) {
              try {
                org.apache.commons.io.FileUtils.cleanDirectory(new File(excelLoadPath));
                LoggerUtils.getLogger().info("清除临时excel文件夹路径: " + excelLoadPath);
              } catch (IOException e) {
                LoggerUtils.getTextareaLogger().error("预加载结束刪除文件夹: " + excelLoadPath + "失败", e);
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
        LoggerUtils.showErrorDialog("生成时发生异常", "找不到目录：" + excelConfigPathField.getText());
        return;
      }
      // 设置为不可用
      toggleTemplateGenBtn(true);
      // 设置进度条长度
      resetPercentageData();
      // 生成前先刷新一遍
      Map<String, File> afterReloadExcelNameMap = FileUtils.listExcelFile(excelPathDir);
      if (afterReloadExcelNameMap.isEmpty()) {
        LoggerUtils.showErrorDialog("生成时发生异常", "目录下: " + excelPathDir.getPath() + " 未找到excel文件");
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
        LoggerUtils.getTextareaLogger().info("excel列表全部渲染完成, 开始准备生成模板文件");
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
        // excel文件生成java文件 并行处理
        ExcelTemplateGenUtils.generateByTemplateParallel(
            reloadExcelFile,
            SELECTED_TEMPLATE_GENERATOR,
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
                    } else {
                      currentProgress.setStyle("-fx-background-color: rgba(219,36,36,0.95)");
                    }
                  });
            });
      }
    } catch (Exception e) {
      LoggerUtils.showErrorDialog("生成配置表异常", ExceptionUtils.getStackTrace(e));
    } finally {
      toggleTemplateGenBtn(false);
      TEMPLATE_IN_PROGRESS.compareAndSet(false, true);
    }
  }

  private void toggleTemplateGenBtn(boolean isClose) {
    textFormBox.setDisable(isClose);
    excelShowListContainer.setDisable(isClose);
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
    LoggerUtils.getTextareaLogger().info("开始查找配置表......");
    // 获取excel文件路径
    File excelPathDir = getExcelDirPath();
    if (excelPathDir == null) {
      return Collections.emptyMap();
    }
    Map<String, File> excelNameFileMap = FileUtils.listExcelFile(excelPathDir);
    if (excelNameFileMap.isEmpty()) {
      LoggerUtils.getTextareaLogger().error("目录: " + excelPathDir + " 下的excel为空");
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
        excelNameFileMap.keySet().stream().sorted(Comparator.comparing(String::toLowerCase)).collect(Collectors.toList());
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
    LoggerUtils.getTextareaLogger().info("查找配置表结束,文件数量总计: " + excelNameFileMap.size());
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
    anchorPane.setStyle("-fx-background-color: #fff");
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
    currentProgress.setStyle("-fx-background-color: #3CED0DFF");
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
      label.setStyle(
          "-fx-border-width: 1;-fx-border-color: "
              + (isLoadSuccess ? ColorDefine.SAFE : ColorDefine.DANGER)
              + ";-fx-border-radius: 3");
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
      LoggerUtils.getTextareaLogger().warn("查找label失败, excelName: " + excelName);
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
    vBox.setStyle(
        "-fx-background-color: white;-fx-border-color: white;-fx-border-width: 0; -fx-padding: 3;"
            + "-fx-selection-bar: white;-fx-selection-bar-non-focused: white;-fx-selection-bar-focused: white;");
    vBox.setSpacing(3);
    return vBox;
  }

  private Label getExcelShowLabel(String excelName) {
    Label label = new Label();
    label.setId(excelName);
    label.setText(excelName.split("\\.")[0]);
    label.setWrapText(true);
    label.setStyle("-fx-border-width: 1;-fx-border-color: #EEE;-fx-border-radius: 3;");
    label.setOnMouseClicked(
        event -> {
          if (EXCEL_SELECTED_LIST.contains(label.getId())) {
            EXCEL_SELECTED_LIST.remove(label.getId());
          } else {
            EXCEL_SELECTED_LIST.add(label.getId());
          }
          if (EXCEL_SELECTED_LIST.contains(label.getId())) {
            label.setStyle("-fx-border-width: 1;-fx-border-color: #F8AB3799;-fx-border-radius: 3;");
          } else {
            label.setStyle("-fx-border-width: 1;-fx-border-color: #EEE;-fx-border-radius: 3;");
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
      LoggerUtils.getTextareaLogger()
          .error(
              "状态切换失败, oldStatus: {}, newStatus: {}",
              this.excelUpdateState.name(),
              excelUpdateState.name());
      throw new RuntimeException(
          "状态切换失败, oldStatus: "
              + this.excelUpdateState.name()
              + ", newStatus: "
              + excelUpdateState.name());
    }
    generateTemplateBtn.setDisable(
        this.excelUpdateState.checkDisable(EExcelUpdateState.GEN_TEMPLATE));
    preLoadBtn.setDisable(this.excelUpdateState.checkDisable(EExcelUpdateState.LOAD_DATA));
    syncConfBtn.setDisable(this.excelUpdateState.checkDisable(EExcelUpdateState.SYNC_DATA));
  }

  public ScrollPane getLogShowScrollPane() {
    return logShowScrollPane;
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
