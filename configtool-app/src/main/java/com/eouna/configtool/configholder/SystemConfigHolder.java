package com.eouna.configtool.configholder;

import com.eouna.configtool.configholder.ConfigDataBean.CacheConfig;
import com.eouna.configtool.configholder.ConfigDataBean.CommonTemplateConf;
import com.eouna.configtool.configholder.ConfigDataBean.ExcelConf;
import com.eouna.configtool.configholder.ConfigDataBean.ExportConf;
import com.eouna.configtool.configholder.ConfigDataBean.JavaTemplateConf;
import com.eouna.configtool.configholder.ConfigDataBean.JsonTemplateConf;
import com.eouna.configtool.configholder.ConfigDataBean.ServerLoadExcelDirConfBean;
import com.eouna.configtool.configholder.ConfigDataBean.SyncConfig;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.FxApplicationContextHolder;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * 系统配置持有者
 *
 * @author CCL
 * @date 2023/3/1
 */
@Component
public class SystemConfigHolder {

  // region============================== 配置信息(自动加载) ==============================

  /** excel相关配置 */
  @ConfigAutowired
  @ConfigSettingControl(desc = "excel生成相关")
  private ExcelConf excelConf;

  /** 导出模式配置(客户端导出/服务端导出) */
  @ConfigAutowired(aliasName = "exportConf")
  @ConfigSettingControl(desc = "导出模式(客户端/服务端)")
  private ExportConf exportConf;

  /** java模板配置 */
  @ConfigAutowired
  @ConfigSettingControl(desc = "java模板配置")
  private JavaTemplateConf javaTemplateConf;

  /** json模板配置 */
  @ConfigAutowired
  @ConfigSettingControl(desc = "json模板配置")
  private JsonTemplateConf jsonTemplateConf;

  /** 通用模板配置 */
  @ConfigAutowired
  @ConfigSettingControl(desc = "通用模板配置")
  private CommonTemplateConf commonTemplateConf;

  /** 服务器同步相关配置 */
  @ConfigAutowired
  @ConfigSettingControl(desc = "服务器同步配置")
  private SyncConfig syncConfig;

  @ConfigAutowired(aliasName = "cache")
  private CacheConfig cacheConfig;

  // endregion============================== 配置信息 ==============================

  /**
   * 是否将配置持久化到文件; 非GUI(命令行)模式下关闭, 避免命令行参数覆盖用户配置 -- SETTER -- 设置是否允许持久化系统配置 -- GETTER -- 是否允许持久化系统配置
   */
  @Getter @Setter private static volatile boolean configPersistEnabled = true;

  /** 服务器模块绑定配置正则 */
  private static final Pattern SERVER_MODULE_BIND_CONF_PATTERN =
      Pattern.compile("^(\\w+)\\((.*)\\)");

  /** excel路径绑定的本地服务器模块信息 */
  private Map<String, List<ServerLoadExcelDirConfBean>> excelDirBindLocalModuleInfo =
      new HashMap<>(8);

  /** 配置字段路径对应的实例引用 */
  private final Map<String, Object> fieldPathOfInstance = new HashMap<>();

  /** 加载配置 */
  public void loadSystemConfig()
      throws IOException,
          IllegalAccessException,
          InstantiationException,
          InvocationTargetException {
    // 解析配置文件
    parseEnvConfig(this);
    // 加载excel路径绑定的本地模块信息
    String localConfigPath =
        FileUtils.getRelatedPathOfRoot(DefaultEnvConfigConstant.LOCAL_LOAD_EXCEL_DIR_CONFIG_PATH);
    excelDirBindLocalModuleInfo = loadServerExcelDirConf(localConfigPath);
  }

  /** 存储系统配置文件信息 */
  public void saveSystemConfigToFile() throws IOException, IllegalAccessException {
    // 非GUI(命令行)模式不落盘, 避免命令行传入的路径覆盖用户配置
    if (!configPersistEnabled) {
      return;
    }
    DumperOptions dumperOptions = createDumperOptions();
    LoaderOptions loaderOptions = createLoaderOptions();
    Yaml yaml =
        new Yaml(
            new SafeConstructor(loaderOptions),
            new Representer(dumperOptions),
            dumperOptions,
            loaderOptions);
    String configPath = getSystemConfigPath();
    // 1. 以"节点树"读取原配置: 保留原有注释、键顺序与缩进结构
    MappingNode rootNode = loadConfigNode(yaml, configPath);
    // 2. 把内存中的配置写回节点树(复用原有节点, 因此注释不会被冲掉)
    List<Field> fields = FieldUtils.getFieldsListWithAnnotation(getClass(), ConfigAutowired.class);
    for (Field field : fields) {
      field.setAccessible(true);
      mergeFieldToNode(rootNode, field, this);
    }
    // 3. 写盘; 固定使用UTF-8, 否则在中文Windows(GBK)下写出后下次启动会因编码不一致解析失败
    try (Writer writer =
        new OutputStreamWriter(new FileOutputStream(configPath), StandardCharsets.UTF_8)) {
      yaml.serialize(rootNode, writer);
    }
  }


  /** 创建yaml输出选项(保留注释) */
  private static DumperOptions createDumperOptions() {
    DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    dumperOptions.setProcessComments(true);
    dumperOptions.setAllowUnicode(true);
    dumperOptions.setMaxSimpleKeyLength(1024);
    dumperOptions.setIndent(2);
    return dumperOptions;
  }

  /** 创建yaml读取选项(保留注释) */
  private static LoaderOptions createLoaderOptions() {
    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setProcessComments(true);
    return loaderOptions;
  }

  /** 读取原配置文件的yaml节点树(文件不存在时返回空的映射节点) */
  private static MappingNode loadConfigNode(Yaml yaml, String configPath) throws IOException {
    File configFile = new File(configPath);
    if (configFile.isFile() && configFile.length() > 0) {
      try (Reader reader =
          new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
        Node node = yaml.compose(reader);
        if (node instanceof MappingNode) {
          return (MappingNode) node;
        }
      }
    }
    return new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
  }

  /**
   * 把字段值合并进配置节点树。
   *
   * <p>复用原有节点(只在必要时新建), 因此节点上的注释得以保留。
   *
   * @param parentNode 父映射节点
   * @param field 配置字段
   * @param instance 字段所属实例
   * @throws IllegalAccessException 非法访问异常
   */
  private void mergeFieldToNode(MappingNode parentNode, Field field, Object instance)
      throws IllegalAccessException {
    field.setAccessible(true);
    Object fieldData = field.get(instance);
    String fieldAliasName = getConfigFieldAliasName(field);
    String fieldName = StringUtils.isEmpty(fieldAliasName) ? field.getName() : fieldAliasName;
    Node existingValueNode = findChildValueNode(parentNode, fieldName);
    Node valueNode = buildValueNode(field.getType(), fieldData, existingValueNode);
    setChildValueNode(parentNode, fieldName, valueNode);
  }

  /** 根据字段类型与当前值构建节点(尽量复用原有节点以保留注释) */
  private Node buildValueNode(Class<?> fieldClass, Object fieldData, Node existingNode) {
    if (List.class.isAssignableFrom(fieldClass)) {
      SequenceNode sequenceNode =
          existingNode instanceof SequenceNode
              ? (SequenceNode) existingNode
              : new SequenceNode(Tag.SEQ, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
      List<Node> elementNodes = new ArrayList<>();
      if (fieldData instanceof List<?>) {
        List<?> itemList = (List<?>) fieldData;
        List<Node> oldElementNodes = sequenceNode.getValue();
        for (int index = 0; index < itemList.size(); index++) {
          Object item = itemList.get(index);
          Node oldElementNode = index < oldElementNodes.size() ? oldElementNodes.get(index) : null;
          elementNodes.add(
              buildValueNode(
                  item == null ? Object.class : item.getClass(), item, oldElementNode));
        }
      }
      sequenceNode.getValue().clear();
      sequenceNode.getValue().addAll(elementNodes);
      return sequenceNode;
    }
    if (fieldClass.getAnnotation(CfgDataBean.class) != null && fieldData != null) {
      MappingNode mappingNode =
          existingNode instanceof MappingNode
              ? (MappingNode) existingNode
              : new MappingNode(Tag.MAP, new ArrayList<>(), DumperOptions.FlowStyle.BLOCK);
      for (Field subField : getConfigurableFields(fieldClass)) {
        try {
          mergeFieldToNode(mappingNode, subField, fieldData);
        } catch (IllegalAccessException e) {
          LoggerUtils.getLogger().error("写回配置字段失败: {}", subField.getName(), e);
        }
      }
      return mappingNode;
    }
    return buildScalarNode(fieldData, existingNode);
  }

  /** 构建标量节点, 并把原节点的注释复制过来 */
  private static Node buildScalarNode(Object fieldData, Node existingNode) {
    DumperOptions.ScalarStyle scalarStyle = DumperOptions.ScalarStyle.PLAIN;
    ScalarNode scalarNode;
    if (fieldData == null) {
      scalarNode = new ScalarNode(Tag.NULL, "null", null, null, scalarStyle);
    } else if (fieldData instanceof Boolean) {
      scalarNode = new ScalarNode(Tag.BOOL, String.valueOf(fieldData), null, null, scalarStyle);
    } else if (fieldData instanceof Float || fieldData instanceof Double) {
      scalarNode = new ScalarNode(Tag.FLOAT, String.valueOf(fieldData), null, null, scalarStyle);
    } else if (fieldData instanceof Number) {
      scalarNode = new ScalarNode(Tag.INT, String.valueOf(fieldData), null, null, scalarStyle);
    } else {
      scalarNode = new ScalarNode(Tag.STR, String.valueOf(fieldData), null, null, scalarStyle);
    }
    if (existingNode != null) {
      // 注释挂在节点上: 值节点是新建的, 需要把原注释搬过来
      scalarNode.setBlockComments(existingNode.getBlockComments());
      scalarNode.setInLineComments(existingNode.getInLineComments());
      scalarNode.setEndComments(existingNode.getEndComments());
    }
    return scalarNode;
  }

  /** 查找映射节点中指定key对应的值节点(忽略大小写) */
  private static Node findChildValueNode(MappingNode mappingNode, String keyName) {
    NodeTuple tuple = findChildTuple(mappingNode, keyName);
    return tuple == null ? null : tuple.getValueNode();
  }

  /** 查找映射节点中指定key的键值对(忽略大小写) */
  private static NodeTuple findChildTuple(MappingNode mappingNode, String keyName) {
    for (NodeTuple tuple : mappingNode.getValue()) {
      Node keyNode = tuple.getKeyNode();
      if (keyNode instanceof ScalarNode
          && ((ScalarNode) keyNode).getValue().equalsIgnoreCase(keyName)) {
        return tuple;
      }
    }
    return null;
  }

  /** 设置映射节点中指定key的值节点(保留原key节点, 使键上的注释不丢失) */
  private static void setChildValueNode(MappingNode mappingNode, String keyName, Node valueNode) {
    List<NodeTuple> tuples = mappingNode.getValue();
    for (int index = 0; index < tuples.size(); index++) {
      NodeTuple tuple = tuples.get(index);
      Node keyNode = tuple.getKeyNode();
      if (keyNode instanceof ScalarNode
          && ((ScalarNode) keyNode).getValue().equalsIgnoreCase(keyName)) {
        tuples.set(index, new NodeTuple(keyNode, valueNode));
        return;
      }
    }
    ScalarNode keyNode =
        new ScalarNode(Tag.STR, keyName, null, null, DumperOptions.ScalarStyle.PLAIN);
    // key节点上的注释取同级已有节点样式, 保证新增字段的注释观感一致
    keyNode.setBlockComments(null);
    tuples.add(new NodeTuple(keyNode, valueNode));
  }

  /**
   * 解析配置文件
   *
   * @throws IOException e
   */
  private void parseEnvConfig(SystemConfigHolder systemConfigHolder)
      throws IOException,
          IllegalAccessException,
          InstantiationException,
          InvocationTargetException {

    Yaml yaml = new Yaml();
    String path = getSystemConfigPath();
    FileInputStream fileInputStream = new FileInputStream(path);
    Map<String, Object> objectMap = yaml.load(fileInputStream);

    List<Field> fields =
        FieldUtils.getFieldsListWithAnnotation(
            systemConfigHolder.getClass(), ConfigAutowired.class);
    for (Field field : fields) {
      String fieldPath = "";
      field.setAccessible(true);
      parseConfRecursion(field, systemConfigHolder, objectMap, fieldPath);
    }

    fileInputStream.close();
  }

  /**
   * 递归解析配置文件中的字段
   *
   * @param field 字段
   * @param fieldInstance 字段对应的对象实例
   * @param objectMap 数据Map
   * @param fieldPath 字段路径
   * @throws InstantiationException 实例化失败时的异常
   * @throws IllegalAccessException 非法访问异常
   * @throws InvocationTargetException 内部异常
   */
  private void parseConfRecursion(
      Field field, Object fieldInstance, Map<String, ?> objectMap, String fieldPath)
      throws InstantiationException, IllegalAccessException, InvocationTargetException {
    Class<?> declaredClass = field.getType();
    // 尝试获取字段别名
    String fieldAliasName = getConfigFieldAliasName(field);
    String fieldName = StringUtils.isEmpty(fieldAliasName) ? field.getName() : fieldAliasName;
    // 配置文件中字段名大小写可能不一致(如serverName写成servername), 这里做一次忽略大小写的兼容匹配
    String matchedKey = findMatchedKey(objectMap, fieldName);
    if (matchedKey != null) {
      Object confData = objectMap.get(matchedKey);
      // List字段的真实类型在泛型中, 需要先解析出元素类型, 否则配置里的Map无法转换成对应bean
      boolean fieldIsList = declaredClass.isAssignableFrom(List.class);
      Class<?> fieldClass = fieldIsList ? resolveElementClass(field) : declaredClass;
      Object newFiledObj = field.get(fieldInstance);
      if (newFiledObj == null) {
        // List的声明类型是接口无法直接实例化, 统一用ArrayList承载
        newFiledObj = fieldIsList ? new ArrayList<>() : getClasNewObject(fieldClass);
      }
      // 所有配置字段需要注解@CfgDataBean
      if (fieldClass.getAnnotation(CfgDataBean.class) != null && newFiledObj != null) {
        // 组装路径
        fieldPath = (fieldPath.isEmpty() ? "" : fieldPath + ".") + fieldClass.getSimpleName();
        field.set(fieldInstance, newFiledObj);
        List<Field> newFiledObjFields = getConfigurableFields(fieldClass);
        if (fieldIsList) {
          for (Object confDatum : ((List<?>) confData)) {
            Object newListFiledObj = getClasNewObject(fieldClass);
            for (Field newFiledObjField : newFiledObjFields) {
              newFiledObjField.setAccessible(true);
              parseConfRecursion(
                  newFiledObjField, newListFiledObj, (Map<String, ?>) confDatum, fieldPath);
            }
            ((List<Object>) newFiledObj).add(newListFiledObj);
          }
        } else {
          for (Field newFiledObjField : newFiledObjFields) {
            newFiledObjField.setAccessible(true);
            parseConfRecursion(newFiledObjField, newFiledObj, (Map<String, ?>) confData, fieldPath);
          }
        }
      } else {
        // 如果有别名则使用别名进行解析
        if (!StringUtils.isEmpty(fieldAliasName)) {
          confData =
              objectMap.containsKey(fieldAliasName) ? objectMap.get(fieldAliasName) : confData;
        }
        field.set(fieldInstance, confData);
        // 保存字段和实例之间的关联
        this.fieldPathOfInstance.put(fieldPath + "." + fieldName, fieldInstance);
      }
    } else {
      throw new RuntimeException(
          "配置表: " + DefaultEnvConfigConstant.SYSTEM_CONFIG_NAME + " 中缺少配置字段: " + fieldName);
    }
  }

  /**
   * 获取配置类中需要读写配置文件的字段。
   *
   * <p>静态字段(如导出模式的取值常量)属于代码常量, 不参与配置文件的读写。
   *
   * @param fieldClass 配置类
   * @return 可配置字段
   */
  private static List<Field> getConfigurableFields(Class<?> fieldClass) {
    List<Field> fields = new ArrayList<>();
    for (Field field : FieldUtils.getAllFieldsList(fieldClass)) {
      if (!Modifier.isStatic(field.getModifiers())) {
        fields.add(field);
      }
    }
    return fields;
  }


  /**
   * 查找配置数据中与字段名匹配的key
   *
   * <p>优先精确匹配, 找不到时退化为忽略大小写匹配(兼容历史配置中字段名大小写不一致的情况)
   *
   * @param objectMap 配置数据
   * @param fieldName 字段名
   * @return 匹配到的key, 未匹配到返回null
   */
  private static String findMatchedKey(Map<String, ?> objectMap, String fieldName) {
    if (objectMap.containsKey(fieldName)) {
      return fieldName;
    }
    for (String key : objectMap.keySet()) {
      if (key != null && key.equalsIgnoreCase(fieldName)) {
        LoggerUtils.getLogger().warn("配置字段 {} 在配置文件中写成了 {}, 请修正大小写", fieldName, key);
        return key;
      }
    }
    return null;
  }

  /**
   * 解析List字段的泛型元素类型
   *
   * @param field 字段
   * @return 元素类型, 无法解析时返回Object
   */
  private static Class<?> resolveElementClass(Field field) {
    Type genericType = field.getGenericType();
    if (genericType instanceof ParameterizedType) {
      Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
      if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
        return (Class<?>) typeArguments[0];
      }
    }
    return Object.class;
  }

  /**
   * 根据class尝试new新的对象
   *
   * @param fieldClass 字段class
   * @return o
   * @throws InvocationTargetException e
   * @throws InstantiationException e
   * @throws IllegalAccessException e
   */
  private Object getClasNewObject(Class<?> fieldClass)
      throws InvocationTargetException, InstantiationException, IllegalAccessException {
    Object newFiledObj = null;
    Constructor<?>[] constructors = fieldClass.getDeclaredConstructors();
    for (Constructor<?> constructor : constructors) {
      if (constructor.getParameterCount() == 0) {
        newFiledObj = constructor.newInstance();
      }
    }
    return newFiledObj;
  }

  /**
   * 获取配置表字段存储别名
   *
   * @param field 字段
   * @return 别名
   */
  private String getConfigFieldAliasName(Field field) {
    if (field.isAnnotationPresent(ConfigAutowired.class)) {
      ConfigAutowired configAutowired = field.getAnnotation(ConfigAutowired.class);
      String fieldAliasName = configAutowired.aliasName();
      if (!StringUtils.isEmpty(fieldAliasName)) {
        return fieldAliasName;
      }
    }
    return "";
  }

  /** 加载excel路径绑定的服务器模块配置 */
  private Map<String, List<ServerLoadExcelDirConfBean>> loadServerExcelDirConf(String configPath) {
    Map<String, List<ServerLoadExcelDirConfBean>> serverLoadExcelDirConfBeanMap = new HashMap<>(8);
    File configFile = new File(configPath);
    try {
      FileReader fileReader = new FileReader(configFile);
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        List<ServerLoadExcelDirConfBean> serverLoadExcelDirConfBeans = new ArrayList<>();
        String[] configLine = line.split("=");
        String excelDir = configLine[0];
        if (configLine.length == 1) {
          serverLoadExcelDirConfBeanMap.put(excelDir, serverLoadExcelDirConfBeans);
          continue;
        }
        // 绑定的服务器模块字符串
        String bindServerModuleStr = configLine[1];
        if (bindServerModuleStr.trim().isEmpty()) {
          serverLoadExcelDirConfBeanMap.put(excelDir, serverLoadExcelDirConfBeans);
          continue;
        }
        String[] bindServerModules = bindServerModuleStr.split("\\+");
        for (String bindServerModule : bindServerModules) {
          ServerLoadExcelDirConfBean confBean = new ServerLoadExcelDirConfBean();
          Matcher matcher = SERVER_MODULE_BIND_CONF_PATTERN.matcher(bindServerModule);
          if (matcher.find()) {
            String bindServerModuleByMatch = matcher.group(1);
            String bindExcelListStr = matcher.group(2);
            confBean.getBindExcelFileList().addAll(Arrays.asList(bindExcelListStr.split(";")));
            confBean.setBindAppModuleDir(bindServerModuleByMatch);
          } else {
            confBean.setBindAppModuleDir(bindServerModule);
          }
          serverLoadExcelDirConfBeans.add(confBean);
        }
        serverLoadExcelDirConfBeanMap.put(excelDir, serverLoadExcelDirConfBeans);
      }
      LoggerUtils.getLogger()
          .info("加载excel绑定服务器模块信息成功,加载配置条数: {}", serverLoadExcelDirConfBeanMap.size());
    } catch (FileNotFoundException e) {
      LoggerUtils.getLogger().error("当前执行文件, 缺少配置文件: {}", configFile.getName());
    } catch (IOException e) {
      LoggerUtils.getLogger().error("读取配置表: " + configFile.getName() + " 发生异常", e);
    }
    return serverLoadExcelDirConfBeanMap;
  }

  /** 获取系统配置路径 */
  private String getSystemConfigPath() {
    return FileUtils.getRelatedPathOfRoot(DefaultEnvConfigConstant.SYSTEM_CONFIG_PATH);
  }

  public ExcelConf getExcelConf() {
    return excelConf;
  }

  public ExportConf getExportConf() {
    return exportConf;
  }

  /**
   * 当前导出模式是否为服务端导出。
   *
   * @return true=服务端导出, false=客户端导出
   */
  public boolean isServerExport() {
    return exportConf != null && exportConf.isServerExport();
  }

  /**
   * 切换导出模式并持久化到配置文件。
   *
   * @param serverExport true=服务端导出, false=客户端导出
   */
  public void switchExportMode(boolean serverExport) {
    exportConf.setExportMode(
        serverExport
            ? DefaultEnvConfigConstant.EXPORT_MODE_SERVER
            : DefaultEnvConfigConstant.EXPORT_MODE_CLIENT);
  }

  /**
   * 当前导出模式对应的"数据范围跳过字符串"。
   *
   * <p>客户端导出时跳过数据范围行标记为 {@code server} 的字段; 服务端导出时跳过标记为 {@code client} 的字段。
   * 所有语言模板(生成期过滤字段 + 运行期跳过列)统一使用该值。
   *
   * @return 需要跳过的数据范围标识
   */
  public String getExportSkipStr() {
    return isServerExport()
        ? excelConf.getDataRangeServerSkipStr()
        : excelConf.getDataRangeClientSkipStr();
  }

  public JavaTemplateConf getJavaTemplateConf() {
    return javaTemplateConf;
  }

  public JsonTemplateConf getJsonTemplateConf() {
    return jsonTemplateConf;
  }

  public CommonTemplateConf getCommonTemplateConf() {
    return commonTemplateConf;
  }

  public SyncConfig getSyncConfig() {
    return syncConfig;
  }

  /**
   * 通过excel相对路径名获取本地模块路径信息
   *
   * @param excelDirName excel相对路径名
   * @return 服务器模块路径信息
   */
  public List<ServerLoadExcelDirConfBean> getLocalModuleDirByExcelDir(String excelDirName) {
    return excelDirBindLocalModuleInfo.get(excelDirName);
  }

  /**
   * 通过配置路径设置配置值
   *
   * @param fieldPath 字段路径
   * @param newVal 新值
   */
  public void updateConfigValByFieldPath(String fieldPath, Object newVal) {
    if (this.fieldPathOfInstance.containsKey(fieldPath)) {
      Object instance = this.fieldPathOfInstance.get(fieldPath);
      String fieldName = fieldPath.substring(fieldPath.lastIndexOf(".") + 1);
      try {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, newVal);
        // 保存配置
        saveSystemConfigToFile();
        LoggerUtils.getLogger().info("更新字段: {}, 的值: {}", fieldName, newVal);
      } catch (NoSuchFieldException | IllegalAccessException | IOException e) {
        LoggerUtils.getLogger().error("不存在字段: " + fieldName + " 当前的配置字段路径: " + fieldPath);
      }
    }
  }

  /**
   * 通过字段路径获取值
   *
   * @param fieldPath 值
   */
  public Object getConfigValByFieldPath(String fieldPath) {
    String fieldName = fieldPath.substring(fieldPath.lastIndexOf(".") + 1);
    if (this.fieldPathOfInstance.containsKey(fieldPath)) {
      Object instance = this.fieldPathOfInstance.get(fieldPath);
      try {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
      } catch (NoSuchFieldException | IllegalAccessException  e) {
        LoggerUtils.getLogger().error("不存在字段: " + fieldName + " 当前的配置字段路径: " + fieldPath);
      }
    }
    throw new RuntimeException("不存在字段: " + fieldName + " 当前的配置字段路径: " + fieldPath);
  }

  public Map<String, Object> getFieldPathOfInstance() {
    return Collections.unmodifiableMap(fieldPathOfInstance);
  }

  /**
   * 单例
   *
   * @return SystemConfigHolder
   */
  public static SystemConfigHolder getInstance() {
    return FxApplicationContextHolder.getContext().getBean(SystemConfigHolder.class);
  }
}
