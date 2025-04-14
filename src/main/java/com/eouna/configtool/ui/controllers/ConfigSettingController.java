package com.eouna.configtool.ui.controllers;

import com.eouna.configtool.configholder.CfgDataBean;
import com.eouna.configtool.configholder.ConfigSettingControl;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.generator.template.ExcelFieldParseAdapter;
import com.eouna.configtool.core.window.BaseWindowController;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.ui.component.ConfigBindTextField;
import com.eouna.configtool.utils.LoggerUtils;
import com.google.common.base.Objects;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * 配置设置控制器
 *
 * @author CCL
 */
public class ConfigSettingController extends BaseWindowController {

  @FXML protected TreeView<Label> settingTree;

  @FXML protected GridPane settingArea;

  /** 配置缓存 */
  private final Map<String, Object> configCache = new HashMap<>(8);

  @FXML
  private void onCancelClick() {
    WindowManager.getInstance().closeWindow(this.getClass());
  }

  @FXML
  private void onConfirm() {
    for (Entry<String, Object> configCacheEntry : configCache.entrySet()) {
      String configFieldPath = configCacheEntry.getKey();
      Object newVal = configCacheEntry.getValue();
      // 更新配置表的值
      SystemConfigHolder.getInstance().updateConfigValByFieldPath(configFieldPath, newVal);
    }
    // 确定后关闭
    WindowManager.getInstance().closeWindow(this.getClass());
  }

  @Override
  public void onCreate(Stage stage) {
    stage.setResizable(false);
  }

  @Override
  public void onMounted(Object... args) {
    super.onMounted(args);
    // 获取所有注解了配置bean的类
    final Set<SettingItem> settingItems = getRootConfigSettingControlField();
    // 构建选择列表
    TreeItem<Label> rootView = new DirectorySettingItem<>(new Label("设置"), null);
    for (SettingItem settingItem : settingItems) {
      addSettingItemToTreeView(rootView, settingItem);
    }
    // 自动展开
    rootView.setExpanded(true);
    settingTree.setRoot(rootView);
    // 进入时初始化配置界面
    for (int rowIdx = 0; rowIdx < rootView.getChildren().size(); rowIdx++) {
      TreeItem<Label> child = rootView.getChildren().get(rowIdx);
      settingArea.add(new Label(child.getValue().getText()), 0, rowIdx);
    }
  }

  /**
   * 将配置节点加入到配置树中
   *
   * @param treeItem 树形组件元素
   * @param settingItem 设置数据元素
   */
  public void addSettingItemToTreeView(TreeItem<Label> treeItem, SettingItem settingItem) {
    Field field = settingItem.getItemField();
    ConfigSettingControl configSettingControl = field.getAnnotation(ConfigSettingControl.class);
    if (!configSettingControl.show()) {
      return;
    }
    String desc = configSettingControl.desc();
    // 如果当前配置上的描述为空则尝试取类上的描述
    if (desc.isEmpty()) {
      Class<?> fieldType = field.getType();
      if (fieldType.isAnnotationPresent(ConfigSettingControl.class)) {
        configSettingControl = fieldType.getAnnotation(ConfigSettingControl.class);
        desc = configSettingControl.desc();
      }
    }
    if (desc.isEmpty()) {
      return;
    }
    // 添加规则: 如果没有子类说明是叶子节点 直接添加 如果有说明是父节点在展开和折叠时需要控制右侧的显示区域显示的内容
    if (settingItem.getChildren().isEmpty()) {
      Label label = new Label(desc);
      label.setPrefWidth(200);
      label.setTextAlignment(TextAlignment.CENTER);
      EntitySettingItem<Label> entitySettingItem =
          new EntitySettingItem<>(label, configSettingControl, settingItem);
      label.setOnMouseClicked((event) -> onSettingItemClick(entitySettingItem).handle(event));
      // 添加具体配置条目的节点
      treeItem.getChildren().add(entitySettingItem);
    } else {
      // 添加目录节点
      TreeItem<Label> childTreeItem = new DirectorySettingItem<>(new Label(desc), settingItem);
      for (SettingItem settingItemChild : settingItem.getChildren()) {
        addSettingItemToTreeView(childTreeItem, settingItemChild);
        if (settingItemChild.getChildren().isEmpty()) {
          // 展开时添加
          treeItem.addEventHandler(
              TreeItem.branchExpandedEvent(), (e) -> getEventHandle().handle(e));
          // 折叠时也添加
          treeItem.addEventHandler(
              TreeItem.branchCollapsedEvent(),
              (event) -> {
                settingArea.getChildren().clear();
                TreeItem<Object> current = event.getSource();
                // 如果还是目录
                for (int rowIdx = 0; rowIdx < current.getChildren().size(); rowIdx++) {
                  TreeItem<Object> child = current.getChildren().get(rowIdx);
                  settingArea.add(new Label(((Label) child.getValue()).getText()), 0, rowIdx);
                }
              });
        }
      }
      treeItem.getChildren().add(childTreeItem);
    }
  }

  /**
   * 获取折叠事件处理类
   *
   * @param <E> e
   * @return 处理类
   */
  private <E extends Event> EventHandler<E> getEventHandle() {
    return event -> {
      settingArea.getChildren().clear();
      TreeItem<Object> current = (TreeItem<Object>) event.getSource();
      boolean isAllDirectorySetting =
          current.getChildren().stream().allMatch(item -> item instanceof DirectorySettingItem);
      // 如果还是目录
      if (isAllDirectorySetting) {
        for (int rowIdx = 0; rowIdx < current.getChildren().size(); rowIdx++) {
          TreeItem<Object> child = current.getChildren().get(rowIdx);
          settingArea.add(new Label(((Label) child.getValue()).getText()), 0, rowIdx);
        }
      } else {
        for (int rowIdx = 0; rowIdx < current.getChildren().size(); rowIdx++) {
          TreeItem<Object> child = current.getChildren().get(rowIdx);
          if (child instanceof EntitySettingItem<?>) {
            EntitySettingItem<?> entitySettingItem = (EntitySettingItem<?>) child;
            updateSettingArea(entitySettingItem, rowIdx);
          }
        }
      }
    };
  }

  /**
   * 当具体某一个配置item点击时，右侧刷新为点击的配置项
   *
   * @param entitySettingItem item
   * @return handler
   * @param <E> event
   */
  private <E extends Event> EventHandler<E> onSettingItemClick(
      EntitySettingItem<Label> entitySettingItem) {
    return event ->
        Platform.runLater(
            () -> {
              settingArea.getChildren().clear();
              updateSettingArea(entitySettingItem, 0);
            });
  }

  /**
   * 更新右侧设置区域显示
   *
   * @param entitySettingItem 设置item
   * @param rowIdx 第几行
   */
  private void updateSettingArea(EntitySettingItem<?> entitySettingItem, int rowIdx) {
    ConfigSettingControl configSettingControl = entitySettingItem.configSettingControl;
    try {
      // 通过注解ConfigSettingControl获取绑定的字段组件
      Class<? extends Control> bindComponent = configSettingControl.bindComponent();
      String fieldPath = getSettingItemFieldPath(entitySettingItem.getSettingItem()).substring(1);
      Control bindControl = bindComponent.getDeclaredConstructor().newInstance();
      // 多种控件
      if (bindControl instanceof TextInputControl) {
        attachTextInputToGridPanel(
            (TextInputControl) bindControl,
            configSettingControl,
            fieldPath,
            entitySettingItem,
            rowIdx);
      }
    } catch (InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      LoggerUtils.showErrorDialog("更新配置时出现异常", e);
      throw new RuntimeException(e);
    }
  }

  /** 文本类输入添加到右侧面板 */
  private void attachTextInputToGridPanel(
      TextInputControl bindControl,
      ConfigSettingControl configSettingControl,
      String fieldPath,
      EntitySettingItem<?> entitySettingItem,
      int rowIdx) {
    Label label = new Label(configSettingControl.desc());
    bindControl.setPromptText(configSettingControl.placeholder());
    // 通过路径获取具体配置值
    bindControl.setText(
        SystemConfigHolder.getInstance().getConfigValByFieldPath(fieldPath).toString());
    bindControl.setOnKeyReleased(
        (eventHandle) ->
            onTextFieldChange(
                eventHandle,
                fieldPath,
                entitySettingItem.getSettingItem().getItemField().getType().getName()));
    bindControl.setPrefWidth(300);
    settingArea.add(label, 0, rowIdx);
    settingArea.add(new Label(": "), 1, rowIdx);
    settingArea.add(bindControl, 2, rowIdx);
  }

  /** 获取类字段中所有注解了ConfigSettingControl的字段 */
  public Set<SettingItem> getRootConfigSettingControlField() {
    final Set<SettingItem> settingItems =
        new ConcurrentSkipListSet<>(Comparator.comparing((o) -> o.getItemField().getName()));
    List<Field> fields =
        FieldUtils.getFieldsListWithAnnotation(
            SystemConfigHolder.class, ConfigSettingControl.class);
    for (Field groupParentSetting : fields) {
      SettingItem settingItem = new SettingItem();
      settingItem.setItemField(groupParentSetting);
      if (buildSettingItemChain(settingItem)) {
        settingItems.add(settingItem);
      }
    }
    return settingItems;
  }

  /**
   * 通过配置item获取配置路径
   *
   * @param settingItem setting
   * @return 用于获取具体配置数值的路径
   */
  private String getSettingItemFieldPath(SettingItem settingItem) {
    if (settingItem == null) {
      return "";
    }
    return getSettingItemFieldPath(settingItem.getParent())
        + "."
        + ((settingItem.getItemField().getType().isAnnotationPresent(CfgDataBean.class))
            ? settingItem.getItemField().getType().getSimpleName()
            : settingItem.getItemField().getName());
  }

  /**
   * 构建配置链
   *
   * @param settingItem 配置item
   * @return 是否找到
   */
  private boolean buildSettingItemChain(SettingItem settingItem) {
    Field field = settingItem.getItemField();
    Class<?> fieldType = field.getType();
    // 如果字段未注解配置类 跳过
    if (!field.isAnnotationPresent(ConfigSettingControl.class)) {
      return false;
    }
    // 如果字段的类是配置类
    if (fieldType.isAnnotationPresent(CfgDataBean.class)) {
      List<Field> childFieldList =
          FieldUtils.getFieldsListWithAnnotation(fieldType, ConfigSettingControl.class);
      // 节点下没有子节点时不加入配置
      if (childFieldList.isEmpty()) {
        return false;
      }
      for (Field childField : childFieldList) {
        SettingItem childSettingItem = new SettingItem();
        childSettingItem.setItemField(childField);
        if (buildSettingItemChain(childSettingItem)) {
          childSettingItem.setParent(settingItem);
          settingItem.getChildren().add(childSettingItem);
        }
      }
    }
    return true;
  }

  /**
   * 当输入框发生变化时将配置字段信息更新
   *
   * @param e 键释放事件
   * @param configFieldPath 配置字段的路径
   * @param fieldType 字段类型
   */
  public void onTextFieldChange(Event e, String configFieldPath, String fieldType) {
    TextInputControl textInputControl = (TextInputControl) e.getSource();
    ExcelFieldParseAdapter adapter = ExcelFieldParseAdapter.getFieldAdapterByTypeStr(fieldType);
    // 解析后的值
    Object parseValue =
        adapter
            .getFieldAdapter()
            .parseFiledStrToJavaClassType(textInputControl.getText(), fieldType);
    // 只是保存最新值,不点确定不保存
    configCache.put(configFieldPath, parseValue);
  }

  @Override
  public void onShow() {
    // 清除一次
    configCache.clear();
    // 查找所有的字段
    Set<Node> allConfigBindTextField =
        stage.getScene().getRoot().lookupAll(ConfigBindTextField.class.getSimpleName());
    for (Node node : allConfigBindTextField) {
      ConfigBindTextField textField = (ConfigBindTextField) node;
      Object val =
          SystemConfigHolder.getInstance()
              .getConfigValByFieldPath(textField.getBindConfigFieldPath());
      textField.setText(val.toString());
    }
  }

  @Override
  public String getFxmlPath() {
    return "config-setting";
  }

  @Override
  public String getStageIconPath() {
    return "icon/settings.png";
  }

  @Override
  public String getTitle() {
    return "设置";
  }

  /** 配置item 双向链表 */
  public static class SettingItem {
    private SettingItem parent;
    private List<SettingItem> children = new ArrayList<>();
    private Field itemField;

    public SettingItem getParent() {
      return parent;
    }

    public void setParent(SettingItem parent) {
      this.parent = parent;
    }

    public List<SettingItem> getChildren() {
      return children;
    }

    public void setChildren(List<SettingItem> children) {
      this.children = children;
    }

    public Field getItemField() {
      return itemField;
    }

    public void setItemField(Field itemField) {
      this.itemField = itemField;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SettingItem)) {
        return false;
      }
      SettingItem that = (SettingItem) o;
      return Objects.equal(getItemField(), that.getItemField());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(getItemField());
    }
  }

  /**
   * tree中的目录节点
   *
   * @author CCL
   * @date 2023/4/19
   */
  public static class DirectorySettingItem<T> extends TreeItem<T> {

    private final SettingItem settingItem;

    public DirectorySettingItem(SettingItem settingItem) {
      this.settingItem = settingItem;
    }

    public DirectorySettingItem(T value, SettingItem settingItem) {
      super(value);
      this.settingItem = settingItem;
    }

    public DirectorySettingItem(T value, Node graphic, SettingItem settingItem) {
      super(value, graphic);
      this.settingItem = settingItem;
    }

    public SettingItem getSettingItem() {
      return settingItem;
    }
  }

  /**
   * tree中的节点
   *
   * @author CCL
   * @date 2023/4/19
   */
  public static class EntitySettingItem<T> extends TreeItem<T> {

    private final SettingItem settingItem;

    private final ConfigSettingControl configSettingControl;

    public EntitySettingItem(ConfigSettingControl configSettingControl, SettingItem settingItem) {
      this.configSettingControl = configSettingControl;
      this.settingItem = settingItem;
    }

    public EntitySettingItem(
        T value, ConfigSettingControl configSettingControl, SettingItem settingItem) {
      super(value);
      this.configSettingControl = configSettingControl;
      this.settingItem = settingItem;
    }

    public EntitySettingItem(
        T value, Node graphic, ConfigSettingControl configSettingControl, SettingItem settingItem) {
      super(value, graphic);
      this.configSettingControl = configSettingControl;
      this.settingItem = settingItem;
    }

    public SettingItem getSettingItem() {
      return settingItem;
    }
  }
}
