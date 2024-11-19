package com.eouna.configtool.ui.component;

import com.eouna.configtool.ui.component.FileDirItemHorizontalBox.ClickStatus;
import com.eouna.configtool.utils.FileUtils;
import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import javax.swing.filechooser.FileSystemView;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客戶端版本更新窗口控制器
 *
 * @author CCL
 * @date 2023/7/28
 */
public class FileChoosePane extends Control implements Initializable {

  // region============================== FXML COMPONENT ==============================
  /** 选择路径列表的滚动pane */
  ScrollPane selectedPathScrollPane = new ScrollPane();
  /** 根节点 */
  AnchorPane root = new AnchorPane();
  /** 文件管理外层容器 */
  ScrollPane fileViewerContainer = new ScrollPane();
  /** 文件管理 */
  GridPane fileViewer = new GridPane();
  /** 展示已选择的文件路径 */
  VBox selectedPathContainer = new VBox();

  // endregion============================== FXML COMPONENT ==============================
  /** 文件打开的当前深度 */
  private int maxFileOpenedDepth;
  /** fileSystemView */
  private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
  /** 选择的文件目录结构 */
  private final List<FileDirItemHorizontalBox> rootFileDirItem = new ArrayList<>();
  /** 已经选择的文件列表 */
  private Set<File> selectedDirList;
  /** 最近操作的文件DIR */
  private FileDirItemHorizontalBox latestOpDirItem = null;
  /** 日志 */
  private final Logger logger = LoggerFactory.getLogger(FileChoosePane.class);
  /** 默认的文件过滤器 */
  private FileFilter fileFilter = FileUtils.DEFAULT_DIR_FILTER;

  /** 文件选择变化时 */
  private final ObjectProperty<EventHandler<FileChooseChangeEvent>> onFileChoose =
      new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
          setEventHandler(FileChooseChangeEvent.EVENT_TYPE, get());
        }

        @Override
        public Object getBean() {
          return FileChoosePane.this;
        }

        @Override
        public String getName() {
          return "onFileChoose";
        }
      };

  /** 是否多选 */
  private final BooleanProperty useMultiSelect = new SimpleBooleanProperty();

  public FileChoosePane() {
    showDirsInFileViewer(null, maxFileOpenedDepth, false, fileFilter);
  }

  public FileChoosePane(FileFilter fileFilter) {
    this.fileFilter = fileFilter;
    showDirsInFileViewer(null, maxFileOpenedDepth, false, fileFilter);
  }

  @Override
  protected Skin<?> createDefaultSkin() {
    SkinBase<?> skinBase =
        new SkinBase<>(this) {
          @Override
          public void dispose() {
            super.dispose();
          }
        };
    initLayOut();
    return skinBase;
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {}

  private void initLayOut() {
    fileViewerContainer.setContent(fileViewer);
    root.getChildren().add(fileViewerContainer);
    // 设置高度
    fileViewerContainer.setPrefHeight(getPrefHeight());
    root.setPrefHeight(getPrefHeight());
    // 设置宽度
    fileViewerContainer.setPrefWidth(getPrefWidth());
    root.setPrefWidth(getPrefWidth());
    // 设置背景图片
    root.setBackground(
        new Background(
            new BackgroundImage(
                new Image(
                    FileUtils.getFullResourceUrl("img/client_back.png"),
                    root.getPrefWidth(),
                    root.getPrefHeight(),
                    true,
                    true),
                BackgroundRepeat.REPEAT,
                BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER,
                BackgroundSize.DEFAULT)));
    getChildren().add(root);
  }

  /**
   * 在panel上展示文件夹列表
   *
   * @param sourceFile 源文件夹
   * @param fileOpenedDepth 文件展示深度
   */
  private void showDirsInFileViewer(
      FileDirItemHorizontalBox sourceFile,
      int fileOpenedDepth,
      boolean needReloadSubDir,
      FileFilter fileFilter) {
    // 先删除多余的列
    if (needReloadSubDir) {
      // 如果需要更新的路径小于最大路径则清理右侧已打开的路径
      if (fileOpenedDepth <= maxFileOpenedDepth) {
        for (int i = maxFileOpenedDepth; i >= fileOpenedDepth; i--) {
          // 删除列
          deleteColumn(fileViewer, i);
        }
      }
      maxFileOpenedDepth = fileOpenedDepth;
    } else {
      maxFileOpenedDepth = Math.max(fileOpenedDepth, maxFileOpenedDepth);
    }
    File[] files;
    if (sourceFile == null) {
      files = File.listRoots();
    } else {
      files = sourceFile.file.listFiles(fileFilter);
      if (files == null) {
        return;
      }
    }
    if (files.length == 0) {
      return;
    }
    VBox fileContainer = new VBox();
    fileContainer.setPrefWidth(200);
    fileContainer.setPadding(new Insets(5, 5, 5, 5));
    for (File file : files) {
      if (!fileFilter.accept(file)) {
        continue;
      }
      String systemDisplayName = fileSystemView.getSystemDisplayName(file);
      FileDirItemHorizontalBox fileDirItem =
          generateNewFileDirItem(sourceFile, file, systemDisplayName, fileOpenedDepth, fileFilter);
      fileContainer.getChildren().add(fileDirItem);
    }
    fileViewer.add(fileContainer, fileOpenedDepth, 0);
  }

  /**
   * 生成文件夹显示Item
   *
   * @param file 文件夹对象
   * @param systemDisplayName 系统展示的名字
   * @param fileOpenedDepth 当前文件深度
   * @return 展示item
   */
  private FileDirItemHorizontalBox generateNewFileDirItem(
      FileDirItemHorizontalBox sourceFile,
      File file,
      String systemDisplayName,
      int fileOpenedDepth,
      FileFilter fileFilter) {
    FileDirItemHorizontalBox fileDirItem = new FileDirItemHorizontalBox();
    String fileName = systemDisplayName;
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null && files.length > 0) {
        boolean isShowFlag = false;
        for (File file1 : files) {
          if (fileFilter.accept(file1)) {
            isShowFlag = true;
            break;
          }
        }
        fileName = isShowFlag ? fileName + "  >" : fileName;
      }
    }
    // 文件夹item
    Label label = new Label(fileName);
    label.setFont(new Font(14));
    FontIcon fontIcon = new FontIcon();
    fontIcon.setIconLiteral("fa-folder-o");
    label.setGraphic(fontIcon);
    // 设置属性
    fileDirItem.dirDepth = fileOpenedDepth;
    fileDirItem.file = file;
    fileDirItem.parentFile = sourceFile;
    // 添加
    fileDirItem.getChildren().add(label);
    fileDirItem.setPrefHeight(10);
    fileDirItem.setCursor(Cursor.HAND);
    FileDirItemHorizontalBox oldFileDirItem = searchFileItem(rootFileDirItem, file);
    if (oldFileDirItem != null) {
      fileDirItem.clickStatus = oldFileDirItem.clickStatus;
    }
    fileDirItem.setStyle("-fx-background-color: " + fileDirItem.clickStatus.backgroundColor);
    fileDirItem.setOnMouseEntered(
        (e) -> {
          FileDirItemHorizontalBox source = ((FileDirItemHorizontalBox) e.getSource());
          if (isNodeSelected(source)) {
            source.setStyle("-fx-background-color: " + source.clickStatus.backgroundColor);
          } else {
            source.setStyle("-fx-background-color: RGB(225, 249, 255)");
          }
        });
    fileDirItem.setOnMouseExited(
        (e) -> {
          FileDirItemHorizontalBox source = ((FileDirItemHorizontalBox) e.getSource());
          source.setStyle("-fx-background-color: " + source.clickStatus.backgroundColor);
        });
    // 点击事件
    fileDirItem.setOnMousePressed(
        (e) -> onFileItemPressedAction(((FileDirItemHorizontalBox) e.getSource())));
    return fileDirItem;
  }

  /**
   * 文件item点击时触发
   *
   * @param source 点击的文件item
   */
  private void onFileItemPressedAction(FileDirItemHorizontalBox source) {
    if (latestOpDirItem != null && !latestOpDirItem.equals(source)) {
      FileDirItemHorizontalBox latestFileDirItem =
          searchFileItem(rootFileDirItem, latestOpDirItem.file);
      if (latestFileDirItem != null) {
        // 将上个操作的item置为选择等待
        latestFileDirItem.clickStatus =
            isNodeSelected(latestFileDirItem)
                ? ClickStatus.SELECTED_WAIT
                : latestFileDirItem.clickStatus;
        latestFileDirItem.setStyle(
            "-fx-background-color: " + latestFileDirItem.clickStatus.backgroundColor);
      }
    }
    if (source.clickStatus == ClickStatus.NONE) {
      source.clickStatus = ClickStatus.READ;
    } else if (source.clickStatus == ClickStatus.SELECTED_DONE) {
      // 取消选择
      source.clickStatus = ClickStatus.NONE;
      renderSelectedFilePath(source);
    } else if (source.clickStatus == ClickStatus.SELECTED_WAIT) {
      // 从选择等待状态切回来的时候需要将状态切为选中完成状态
      source.clickStatus = ClickStatus.SELECTED_DONE;
      renderSelectedFilePath(source);
    } else {
      source.clickStatus = ClickStatus.SELECTED_DONE;
      renderSelectedFilePath(source);
    }
    // 点击之后
    source.setStyle("-fx-background-color: " + source.clickStatus.backgroundColor);
    // 展示文件列表item
    showDirsInFileViewer(source, source.dirDepth + 1, true, this.fileFilter);
    waitScrollPaneToRight();
    // 更新已选择的叶子节点到选择列表panel,使用异步避免阻塞
    Platform.runLater(this::refreshedOnSelectedPathChange);
    latestOpDirItem = source;
  }

  /**
   * 通过文件对象搜索文件item
   *
   * @param nodeList 搜索开始列表
   * @param file 目标文件对象
   * @return 文件item
   */
  private FileDirItemHorizontalBox searchFileItem(
      List<FileDirItemHorizontalBox> nodeList, File file) {
    if (nodeList.isEmpty()) {
      return null;
    }
    FileDirItemHorizontalBox fileDirItem = null;
    for (FileDirItemHorizontalBox dirItem : nodeList) {
      if (dirItem.file.getAbsolutePath().equals(file.getAbsolutePath())) {
        return dirItem;
      }
      fileDirItem = searchFileItem(dirItem.childList, file);
      if (fileDirItem != null) {
        break;
      }
    }
    return fileDirItem;
  }

  /**
   * 重新渲染选中的文件路径 添加时单向向上搜索 删除时向上和向下搜索
   *
   * @param source 当前操作的节点
   */
  private void renderSelectedFilePath(FileDirItemHorizontalBox source) {
    FileDirItemHorizontalBox rootDirItem = source;
    boolean needUpdateParent = false;
    while ((rootDirItem.parentFile != null) && !needUpdateParent) {
      FileDirItemHorizontalBox parentFile = rootDirItem.parentFile;
      // 看当前的节点是否在父节点的子节点列表中
      boolean isContain = false;
      for (FileDirItemHorizontalBox dirItem : parentFile.childList) {
        if (rootDirItem.equals(dirItem)) {
          isContain = true;
          break;
        }
      }
      // 如果不存在则加入父节点的子节点列表中
      if (!isContain) {
        parentFile.childList.add(rootDirItem);
        // 首次加入肯定是选中
        parentFile.clickStatus =
            isNodeSelected(rootDirItem) ? ClickStatus.SELECTED_WAIT : rootDirItem.clickStatus;
        parentFile.setStyle("-fx-background-color: " + parentFile.clickStatus.backgroundColor);
      } else {
        if (!isNodeSelected(source)) {
          // 如果节点没有被选择则需要从父节点的子节点列表中移除，如果子节点移除后为空需要判断是否移除
          parentFile.childList.removeIf(rootDirItem::equals);
          // 如果子节点为空则清除父节点的选中状态
          if (parentFile.childList.isEmpty()) {
            parentFile.clickStatus = ClickStatus.NONE;
            parentFile.setStyle("-fx-background-color: " + parentFile.clickStatus.backgroundColor);
          } else {
            // 如果父节点移除当前节点后还有子节点则停止更新上层的节点
            needUpdateParent = true;
          }
        } else {
          needUpdateParent = parentFile.clickStatus == ClickStatus.SELECTED_WAIT;
          if (!needUpdateParent) {
            parentFile.clickStatus = ClickStatus.SELECTED_WAIT;
          }
          // 如果是选中状态则更新就行
          parentFile.setStyle("-fx-background-color: " + parentFile.clickStatus.backgroundColor);
        }
      }
      rootDirItem = parentFile;
    }
    // 如果是节点取消选择且子节点不为空 需要删除子节点下的数据
    if (!isNodeSelected(source) && !source.childList.isEmpty()) {
      // 更新子节点的状态
      cancelAllChildFileItem(source);
    }
    // 根节点添加
    if (!rootFileDirItem.contains(rootDirItem)) {
      rootFileDirItem.add(rootDirItem);
    }
  }

  /**
   * 通过文件对象搜索文件item
   *
   * @param cancelNode 搜索开始列表
   */
  private void cancelAllChildFileItem(FileDirItemHorizontalBox cancelNode) {
    List<FileDirItemHorizontalBox> nodeList = cancelNode.childList;
    if (nodeList.isEmpty()) {
      return;
    }
    for (FileDirItemHorizontalBox dirItem : nodeList) {
      dirItem.clickStatus = ClickStatus.NONE;
      dirItem.setStyle("-fx-background-color: " + dirItem.clickStatus.backgroundColor);
      cancelAllChildFileItem(dirItem);
    }
  }

  /**
   * 删除列
   *
   * @param gridPane 格子面板
   * @param columnIndex 列index
   */
  private void deleteColumn(GridPane gridPane, int columnIndex) {
    // 遍历 GridPane，找到第 columnIndex 列的所有子节点，然后从 GridPane 中移除它们
    gridPane.getChildren().removeIf(node -> GridPane.getColumnIndex(node) == columnIndex);
    // 更新其他列的列索引，以填充删除列后的空白
    gridPane
        .getChildren()
        .forEach(
            node -> {
              Integer colIndex = GridPane.getColumnIndex(node);
              if (colIndex > columnIndex) {
                GridPane.setColumnIndex(node, colIndex - 1);
              }
            });
  }

  /** 当选择列表路径改变时,进行刷新 */
  private void refreshedOnSelectedPathChange() {
    // 先清除旧的
    selectedPathContainer.getChildren().clear();
    Set<FileDirItemHorizontalBox> fileDirItems = getAllSelectedLeafDirItem(rootFileDirItem);
    // 重排序
    fileDirItems =
        fileDirItems.stream()
            .sorted(Comparator.comparing(o -> o.file.getAbsolutePath()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    int itemHeight = 20;
    for (FileDirItemHorizontalBox fileDirItem : fileDirItems) {
      // 单行路径选择item
      HBox selectedPathItem = new HBox();
      selectedPathItem.setStyle("-fx-background-color: #FFF");
      selectedPathItem.setOnMouseEntered(
          (e) -> ((HBox) (e.getSource())).setStyle("-fx-background-color: RGB(225, 249, 255)"));
      selectedPathItem.setOnMouseExited(
          (e) -> ((HBox) (e.getSource())).setStyle("-fx-background-color: #FFF"));
      // 路径名
      Label pathName = new Label(fileDirItem.file.getAbsolutePath());
      pathName.setFont(new Font(16));
      pathName.setPrefHeight(itemHeight);
      selectedPathItem.getChildren().add(pathName);
      // 取消按钮
      Label cancelLabel = new Label("x");
      cancelLabel.setFont(new Font(12));
      cancelLabel.setPrefWidth(20);
      cancelLabel.setPrefHeight(itemHeight);
      cancelLabel.setCursor(Cursor.HAND);
      cancelLabel.setTextAlignment(TextAlignment.LEFT);
      cancelLabel.setAlignment(Pos.TOP_RIGHT);
      cancelLabel.setOnMouseClicked(
          (e) -> {
            FileDirItemHorizontalBox newFileDirItem =
                searchFileItem(rootFileDirItem, fileDirItem.file);
            if (newFileDirItem != null) {
              // 取消选择
              newFileDirItem.clickStatus = ClickStatus.NONE;
              // 重新渲染选择的文件路径
              renderSelectedFilePath(newFileDirItem);
              newFileDirItem.setStyle(
                  "-fx-background-color: " + newFileDirItem.clickStatus.backgroundColor);
              // 展示文件列表item
              showDirsInFileViewer(
                  newFileDirItem.parentFile,
                  newFileDirItem.parentFile.dirDepth + 1,
                  true,
                  this.fileFilter);
              waitScrollPaneToRight();
              // 更新已选择的叶子节点到选择列表panel,使用异步避免阻塞
              Platform.runLater(this::refreshedOnSelectedPathChange);
            }
          });
      selectedPathItem.getChildren().add(cancelLabel);
      selectedPathContainer.getChildren().add(selectedPathItem);
      selectedPathScrollPane.setVvalue(1.0D);
    }
    selectedDirList =
        fileDirItems.stream().map(fileDirItem -> fileDirItem.file).collect(Collectors.toSet());
    FileChooseChangeEvent chooseChangeEvent = new FileChooseChangeEvent();
    chooseChangeEvent.setSelectedDirList(selectedDirList);
    fireEvent(chooseChangeEvent);
  }

  /**
   * 获取所有选择的叶子节点
   *
   * @return 叶子节点
   */
  private Set<FileDirItemHorizontalBox> getAllSelectedLeafDirItem(
      List<FileDirItemHorizontalBox> nodeList) {
    Set<FileDirItemHorizontalBox> leafNodeList = new LinkedHashSet<>();
    for (FileDirItemHorizontalBox dirItem : nodeList) {
      if (!dirItem.childList.isEmpty()) {
        Set<FileDirItemHorizontalBox> selectedLeafDirItemList =
            getAllSelectedLeafDirItem(dirItem.childList);
        // 如果子节点不为空则添加所有的子节点
        if (!selectedLeafDirItemList.isEmpty()) {
          leafNodeList.addAll(selectedLeafDirItemList);
        } else if (isNodeSelected(dirItem)) {
          // 如果子节点为空还需要添加当前节点
          leafNodeList.add(dirItem);
        }
      } else if (isNodeSelected(dirItem)) {
        leafNodeList.add(dirItem);
      }
    }
    return leafNodeList;
  }

  /** 等待滚动列表移动到最右侧 */
  private void waitScrollPaneToRight() {
    Platform.runLater(
        () -> {
          while (fileViewerContainer.getHvalue() != 1.0) {
            fileViewerContainer.setHvalue(1.0);
          }
        });
  }

  private boolean isNodeSelected(FileDirItemHorizontalBox dirItem) {
    return dirItem.clickStatus == ClickStatus.SELECTED_DONE
        || dirItem.clickStatus == ClickStatus.SELECTED_WAIT;
  }

  public static class FileChooseChangeEvent extends Event {

    /** 选择文件夹的列表 */
    private Set<File> selectedDirList;

    public static final EventType<FileChooseChangeEvent> EVENT_TYPE =
        new EventType<>(Event.ANY, "CHANGE_ACTION");

    public FileChooseChangeEvent() {
      super(EVENT_TYPE);
    }

    public Set<File> getSelectedDirList() {
      return selectedDirList;
    }

    public void setSelectedDirList(Set<File> selectedDirList) {
      this.selectedDirList = selectedDirList;
    }
  }

  public final ObjectProperty<EventHandler<FileChooseChangeEvent>> onFileChooseProperty() {
    return onFileChoose;
  }

  public final void setOnFileChoose(EventHandler<FileChooseChangeEvent> value) {
    onFileChooseProperty().set(value);
  }

  public void setFileFilter(FileFilter fileFilter) {
    this.fileFilter = fileFilter;
  }

  public final EventHandler<FileChooseChangeEvent> getOnFileChoose() {
    return onFileChooseProperty().get();
  }

  public boolean isUseMultiSelect() {
    return useMultiSelect.get();
  }

  public BooleanProperty useMultiSelectProperty() {
    return useMultiSelect;
  }

  public void setUseMultiSelect(boolean useMultiSelect) {
    this.useMultiSelect.set(useMultiSelect);
  }
}
