package com.eouna.configtool.configholder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
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

import com.eouna.configtool.configholder.ConfigDataBean.CacheConfig;
import com.eouna.configtool.configholder.ConfigDataBean.JsonTemplateConf;
import com.eouna.configtool.configholder.ConfigDataBean.ServerLoadExcelDirConfBean;
import com.eouna.configtool.configholder.ConfigDataBean.SyncConfig;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.configholder.ConfigDataBean.ExcelConf;
import com.eouna.configtool.configholder.ConfigDataBean.JavaTemplateConf;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.LoggerUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * 系统配置持有者
 *
 * @author CCL
 * @date 2023/3/1
 */
public class SystemConfigHolder {

  // region============================== 配置信息(自动加载) ==============================

  /** excel相关配置 */
  @ConfigAutowired
  @ConfigSettingControl(desc = "excel生成相关")
  private ExcelConf excelConf;

  /** java模板配置 */
  @ConfigAutowired
  @ConfigSettingControl(desc = "java模板配置")
  private JavaTemplateConf javaTemplateConf;

  /** lua模板配置 */
  @ConfigAutowired
  @ConfigSettingControl(desc = "lua模板配置")
  private JsonTemplateConf jsonTemplateConf;

  /** 服务器同步相关配置 */
  @ConfigAutowired
  @ConfigSettingControl(desc = "服务器同步配置")
  private SyncConfig syncConfig;

  @ConfigAutowired(aliasName = "cache")
  private CacheConfig cacheConfig;

  // endregion============================== 配置信息 ==============================

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
    SystemConfigHolder systemConfigHolder = getInstance();
    // 解析配置文件
    parseEnvConfig(systemConfigHolder);
    // 加载excel路径绑定的本地模块信息
    String localConfigPath =
        FileUtils.getRelatedPathOfRoot(DefaultEnvConfigConstant.LOCAL_LOAD_EXCEL_DIR_CONFIG_PATH);
    excelDirBindLocalModuleInfo = loadServerExcelDirConf(localConfigPath);
  }

  /** 存储系统配置文件信息 */
  public void saveSystemConfigToFile() throws IOException, IllegalAccessException {
    DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    dumperOptions.setProcessComments(true);
    dumperOptions.setAllowUnicode(true);
    dumperOptions.setMaxSimpleKeyLength(1024);
    LoaderOptions loaderOptions = new LoaderOptions();
    loaderOptions.setProcessComments(true);
    Yaml yaml =
        new Yaml(
            new SafeConstructor(loaderOptions),
            new Representer(dumperOptions),
            dumperOptions,
            loaderOptions);
    FileWriter writer = new FileWriter(getSystemConfigPath());
    SystemConfigHolder systemConfigHolder = getInstance();
    Map<String, Object> objectMap = new HashMap<>(8);
    List<Field> fields =
        FieldUtils.getFieldsListWithAnnotation(
            systemConfigHolder.getClass(), ConfigAutowired.class);
    fields.sort(Comparator.comparing(Field::getName).reversed());
    for (Field field : fields) {
      field.setAccessible(true);
      saveFieldAllData(field, systemConfigHolder, objectMap);
    }
    if (!objectMap.isEmpty()) {
      yaml.dump(objectMap, writer);
    }
  }

  /**
   * 存储所有数据
   *
   * @param field 字段
   * @param fieldInstance 字段对应的对象实例
   * @param objectMap 数据Map
   * @throws IllegalAccessException 非法访问异常
   */
  public void saveFieldAllData(Field field, Object fieldInstance, Map<String, Object> objectMap)
      throws IllegalAccessException {
    Object fieldData = field.get(fieldInstance);
    String fieldAliasName = getConfigFieldAliasName(field);
    String fieldName = StringUtils.isEmpty(fieldAliasName) ? field.getName() : fieldAliasName;
    Class<?> fieldClass = field.getType();
    if (fieldClass.getAnnotation(CfgDataBean.class) != null) {
      Map<String, Object> dataMap = new LinkedHashMap<>(8);
      objectMap.put(fieldName, dataMap);
      List<Field> fields = FieldUtils.getAllFieldsList(fieldClass);
      for (Field subField : fields) {
        subField.setAccessible(true);
        saveFieldAllData(subField, fieldData, dataMap);
      }
    } else {
      objectMap.put(fieldName, fieldData);
    }
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
    Class<?> fieldClass = field.getType();
    // 尝试获取字段别名
    String fieldAliasName = getConfigFieldAliasName(field);
    String fieldName = StringUtils.isEmpty(fieldAliasName) ? field.getName() : fieldAliasName;
    if (objectMap.containsKey(fieldName)) {
      Object confData = objectMap.get(fieldName);
      Object newFiledObj = field.get(fieldInstance);
      if (newFiledObj == null) {
        newFiledObj = getClasNewObject(fieldClass);
      }
      boolean fieldIsList = false;
      if (fieldClass.isAssignableFrom(List.class)) {
        ParameterizedType type = (ParameterizedType) field.getGenericType();
        fieldClass = (Class<?>) type.getActualTypeArguments()[0];
        fieldIsList = true;
      }
      // 所有配置字段需要注解@CfgDataBean
      if (fieldClass.getAnnotation(CfgDataBean.class) != null && newFiledObj != null) {
        // 组装路径
        fieldPath = (fieldPath.isEmpty() ? "" : fieldPath + ".") + fieldClass.getSimpleName();
        field.set(fieldInstance, newFiledObj);
        List<Field> newFiledObjFields = FieldUtils.getAllFieldsList(fieldClass);
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
      LoggerUtils.getTextareaLogger()
          .info("加载excel绑定服务器模块信息成功,加载配置条数: {}", serverLoadExcelDirConfBeanMap.size());
    } catch (FileNotFoundException e) {
      LoggerUtils.getTextareaLogger().error("当前执行文件, 缺少配置文件: {}", configFile.getName());
    } catch (IOException e) {
      LoggerUtils.getTextareaLogger().error("读取配置表: " + configFile.getName() + " 发生异常", e);
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

  public JavaTemplateConf getJavaTemplateConf() {
    return javaTemplateConf;
  }

  public JsonTemplateConf getJsonTemplateConf() {
    return jsonTemplateConf;
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
      } catch (NoSuchFieldException | IllegalAccessException e) {
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
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final SystemConfigHolder instance;

    Singleton() {
      this.instance = new SystemConfigHolder();
    }

    public SystemConfigHolder getInstance() {
      return instance;
    }
  }
}
