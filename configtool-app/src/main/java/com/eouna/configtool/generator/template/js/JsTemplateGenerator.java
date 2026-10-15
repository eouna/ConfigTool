package com.eouna.configtool.generator.template.js;

import com.eouna.configtool.configholder.ConfigDataBean.JavaTemplateConf;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.generator.base.ExcelFileStructure;
import com.eouna.configtool.generator.bean.ExcelSheetBean;
import com.eouna.configtool.generator.exceptions.ExcelParseException;
import com.eouna.configtool.generator.template.AbstractTemplateGenerator;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.generator.template.ExcelFieldParseAdapter;
import com.eouna.configtool.utils.ExcelUtils;
import com.eouna.configtool.utils.FileUtils;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * JavaScript模板生成器。
 *
 * <p>JavaScript运行期直接读取工具导出的json数据(与json模板共用同一份数据), 因此本生成器不写数据, 只生成
 * bean元数据 / 容器 / 数据管理器等读取代码, 数据由 {@code json} 目录提供。
 *
 * <p>输出目录结构:
 *
 * <pre>
 * js/
 * ├── package.json
 * ├── JsonLoader.js          数据源加载器(Node fs / 浏览器 fetch / 内存)
 * ├── Enums.js               枚举定义
 * ├── constants_xxx.js      常量配置(有常量表时生成)
 * ├── bean/xxxCfg.js        每张表的配置bean
 * ├── container/xxxCfgContainer.js  每张表的容器
 * ├── GameDataManager.js    配置数据管理器
 * └── debug/main.js         临时调试入口
 * </pre>
 *
 * @author CCL
 */
public class JsTemplateGenerator extends AbstractTemplateGenerator {

  /** 输出根目录 */
  private static final String JS_DIR = "js";
  /** bean目录 */
  private static final String BEAN_DIR = JS_DIR + File.separator + DefaultEnvConfigConstant.CFG_BEAN_PATH;
  /** 容器目录 */
  private static final String CONTAINER_DIR =
      JS_DIR + File.separator + DefaultEnvConfigConstant.CONTAINER_PATH;
  /** 调试目录 */
  private static final String DEBUG_DIR = JS_DIR + File.separator + "debug";

  /** 枚举(枚举名 -> 值) */
  private final Map<String, Set<String>> enumCache = new ConcurrentSkipListMap<>();
  /** 容器定义 */
  private final Map<String, ContainerDef> containerDefMap = new ConcurrentSkipListMap<>();
  /** 已生成bean, 用于检查重复工作薄名 */
  private final Set<String> generatedBeanSet = ConcurrentHashMap.newKeySet();

  @Override
  protected String getTemplateBindRelatedPath() {
    return ETemplateGenerator.JS_GENERATOR.getTemplateHandler().getTemplateBindRelatedPath();
  }

  @Override
  public void generatorBefore(
      List<File> successGenList,
      Map<File, ExcelFileStructure> excelFileStructureMap,
      List<Exception> exceptionCollector) {
    try {
      enumCache.clear();
      containerDefMap.clear();
      generatedBeanSet.clear();
      FileUtils.getOrCreateDir(getTargetDir() + JS_DIR);
      FileUtils.getOrCreateDir(getTargetDir() + BEAN_DIR);
      FileUtils.getOrCreateDir(getTargetDir() + CONTAINER_DIR);
      FileUtils.getOrCreateDir(getTargetDir() + DEBUG_DIR);

      String date = getGenerateDate();
      // 数据源加载器(依赖json的导出方式)
      Map<String, Object> loaderDataMap = new HashMap<>(4);
      loaderDataMap.put("date", date);
      loaderDataMap.put(
          "isSplitMultiJson", SystemConfigHolder.getInstance().getJsonTemplateConf().isSplitMultiJson());
      generateTemplate(
          loaderDataMap, "JsonLoader.ftl", getTargetDir() + JS_DIR + File.separator + "JsonLoader.js");

      // 容器基类
      Map<String, Object> baseContainerDataMap = new HashMap<>(2);
      baseContainerDataMap.put("date", date);
      generateTemplate(
          baseContainerDataMap,
          "BaseCfgContainer.ftl",
          getTargetDir() + CONTAINER_DIR + File.separator + "BaseCfgContainer.js");

      // package.json(type: module)
      Map<String, Object> packageDataMap = new HashMap<>(2);
      packageDataMap.put("date", date);
      generateTemplate(
          packageDataMap, "PackageJson.ftl", getTargetDir() + JS_DIR + File.separator + "package.json");

      // 分表的父节点是虚拟文件(磁盘上不存在), 需要额外生成父级bean与容器
      Set<String> generatedParentList = new HashSet<>();
      for (Map.Entry<File, ExcelFileStructure> entry : excelFileStructureMap.entrySet()) {
        File currentDealFile = entry.getKey();
        if (currentDealFile.exists() || !generatedParentList.add(currentDealFile.getName())) {
          continue;
        }
        ExcelSheetBean parentSheetBean =
            new ExcelSheetBean(currentDealFile, currentDealFile.getName());
        generatorOneExcelFile(currentDealFile, null, null, parentSheetBean, entry.getValue());
      }    } catch (Exception e) {
      LoggerUtils.getLogger().error("初始化JavaScript输出目录异常", e);
      exceptionCollector.add(e);
    }
  }

  @Override
  public void generatorOneExcelFile(
      File file,
      Workbook workbook,
      Sheet sheet,
      ExcelSheetBean sheetBean,
      ExcelFileStructure excelFileStructure)
      throws Exception {
    // 父节点(分表)没有真实的工作薄, 字段从子表合并而来
    boolean isParentNode = sheet == null;
    String sheetName = sheetBean.getSheetName();
    String beanClassName = getCfgBeanClassName(sheetName);
    if (!generatedBeanSet.add(beanClassName)) {
      throw new ExcelParseException("生成JavaScript Bean失败, 存在重复的工作薄名: " + sheetName);
    }
    String beanModule = toLowerFirst(beanClassName);
    String containerClassName = beanClassName + DefaultEnvConfigConstant.CONTAINER_GEN_SUFFIX;
    String containerModule = toLowerFirst(containerClassName);
    String constantsClassName = beanClassName + "Constants";
    String constantsModule = "constants_" + beanModule;

    // 字段元数据(只取表头, 不解析数据行)
    List<Map<String, String>> fields = loadFields(file, sheet, excelFileStructure, isParentNode);
    if (fields.isEmpty()) {
      throw new ExcelParseException("JavaScript配置表没有可用字段: " + sheetName);
    }

    Map<String, Object> beanDataMap = new HashMap<>(8);
    beanDataMap.put("beanClassName", beanClassName);
    beanDataMap.put("excelName", file.getName());
    beanDataMap.put("sheetName", sheetName);
    beanDataMap.put("idName", getJavaTemplateConf().getBaseBeanIdName());
    beanDataMap.put("fields", fields);
    beanDataMap.put("date", getGenerateDate());
    generateTemplate(
        beanDataMap,
        "Bean.ftl",
        getTargetDir() + BEAN_DIR + File.separator + beanModule + ".js");

    // 常量配置(来自excel中的常量工作薄)
    List<Map<String, String>> constantFields = loadConstantFields(workbook);
    boolean hasConstants = !constantFields.isEmpty();
    if (hasConstants) {
      StringBuilder constantBody = new StringBuilder();
      for (Map<String, String> constant : constantFields) {
        constantBody
            .append("  ")
            .append(quote(constant.get("name")))
            .append(": ")
            .append(constant.get("literal"))
            .append(",\n");
      }
      Map<String, Object> constantsDataMap = new HashMap<>(8);
      constantsDataMap.put("beanClassName", beanClassName);
      constantsDataMap.put("constantsClassName", constantsClassName);
      constantsDataMap.put("sheetName", sheetName);
      constantsDataMap.put("body", constantBody.toString());
      constantsDataMap.put("date", getGenerateDate());
      generateTemplate(
          constantsDataMap,
          "Constants.ftl",
          getTargetDir() + JS_DIR + File.separator + constantsModule + ".js");
    }

    // 容器: 绑定excel文件 + 读取json数据
    Map<String, Object> containerDataMap = new HashMap<>(16);
    containerDataMap.put("containerClassName", containerClassName);
    containerDataMap.put("beanClassName", beanClassName);
    containerDataMap.put("beanModule", beanModule);
    containerDataMap.put("sheetName", sheetName);
    containerDataMap.put("excelName", file.getName());
    containerDataMap.put("hasConstants", hasConstants);
    containerDataMap.put("constantsClassName", constantsClassName);
    containerDataMap.put("bindExcelList", getBindExcelList(file, excelFileStructure, isParentNode));
    containerDataMap.put(
        "hasRelatedTable", isParentNode || !excelFileStructure.getParentFile().equals(file));
    containerDataMap.put("isParentNode", isParentNode);
    containerDataMap.put("date", getGenerateDate());
    generateTemplate(
        containerDataMap,
        "Container.ftl",
        getTargetDir() + CONTAINER_DIR + File.separator + containerModule + ".js");

    containerDefMap.put(
        containerClassName, new ContainerDef(containerClassName, toLowerFirst(containerClassName), containerModule));
  }

  @Override
  public void generatorAfter(
      TextAreaLogger textAreaLogger, Map<File, ExcelFileStructure> excelFileStructureMap) {
    try {
      String targetDir = getTargetDir();
      String date = getGenerateDate();
      Collection<ContainerDef> containerDefs = containerDefMap.values();

      // 枚举
      List<EnumDef> enumDefs = new ArrayList<>();
      enumCache.forEach(
          (name, values) -> enumDefs.add(new EnumDef(name, new ArrayList<>(values))));
      Map<String, Object> enumDataMap = new HashMap<>(4);
      enumDataMap.put("enumDefs", enumDefs);
      enumDataMap.put("date", date);
      generateTemplate(enumDataMap, "Enums.ftl", targetDir + JS_DIR + File.separator + "Enums.js");

      JavaTemplateConf templateConf = getJavaTemplateConf();
      // 数据管理器
      Map<String, Object> managerDataMap = new HashMap<>(8);
      managerDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      managerDataMap.put("containerDefs", containerDefs);
      managerDataMap.put("loadMethodName", templateConf.getDataManagerLoadDataCaller());
      managerDataMap.put("date", date);
      generateTemplate(
          managerDataMap, "GameDataManager.ftl", targetDir + JS_DIR + File.separator + "GameDataManager.js");

      // 调试入口
      Map<String, Object> debugDataMap = new HashMap<>(8);
      debugDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      debugDataMap.put("containerDefs", containerDefs);
      debugDataMap.put("loadMethodName", templateConf.getDataManagerLoadDataCaller());
      debugDataMap.put("date", date);
      debugDataMap.put("printLoadData", isPrintLoadData());
      // 主入口代码是否导出由命令行 --export_main / 界面控制
      if (isExportMainEntry()) {
  generateTemplate(debugDataMap, "DebugMain.ftl", targetDir + DEBUG_DIR + File.separator + "main.js");
      }

      if (textAreaLogger != null) {
        textAreaLogger.info("生成JavaScript模板文件结束(数据源: json)");
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成JavaScript管理器/枚举时发生异常", e);
    } finally {
      generatedBeanSet.clear();
    }
  }

  /** 读取表头字段元数据(数据源是json, 因此这里不解析数据行) */
  /**
   * 读取表头字段元数据(数据源是json, 因此这里不解析数据行)
   *
   * @param file 当前处理的excel文件
   * @param sheet 当前工作薄(父节点为null)
   * @param excelFileStructure 文件结构
   * @param isParentNode 是否为分表父节点
   */
  private List<Map<String, String>> loadFields(
      File file, Sheet sheet, ExcelFileStructure excelFileStructure, boolean isParentNode)
      throws Exception {
    int descRow = SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldDescRow();
    int typeRow = SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldTypeRow();
    int nameRow = SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldNameRow();
    // 按当前「导出选项」过滤字段: 客户端导出跳过 server 字段, 服务端导出跳过 client 字段
    String clientSkipStr = SystemConfigHolder.getInstance().getExportSkipStr();

    // 父节点没有工作薄, 字段由子表合并而来
    Collection<File> excelFileList =
        isParentNode ? excelFileStructure.getChildFileSet() : Collections.singletonList(file);

    List<Map<String, String>> fields = new ArrayList<>();
    Set<String> generatedFieldNames = new HashSet<>();
    for (File excelFile : excelFileList) {
      try (Workbook workbook = WorkbookFactory.create(excelFile, null, true)) {
        Sheet currentSheet = workbook.getSheetAt(0);
        Set<Integer> skipColList = ExcelTemplateGenUtils.getSkipCellList(currentSheet, clientSkipStr);
        int lastCol =
            currentSheet.getRow(nameRow) == null ? 0 : currentSheet.getRow(nameRow).getLastCellNum();
        for (int col = 0; col < lastCol; col++) {
          if (skipColList.contains(col)) {
            continue;
          }
          String name = cellText(currentSheet, nameRow, col);
          String rawType = cellText(currentSheet, typeRow, col);
          if (name.isEmpty() || rawType.isEmpty()) {
            continue;
          }
          // 分表父节点合并子表字段时去重
          if (!generatedFieldNames.add(name)) {
            continue;
          }
          collectEnum(rawType);
          Map<String, String> field = new LinkedHashMap<>();
          field.put("name", name);
          field.put("rawType", escapeText(rawType));
          field.put("type", toJsType(rawType));
          field.put("desc", escapeText(cellText(currentSheet, descRow, col)));
          fields.add(field);
        }
      }
    }
    return fields;
  }

  /** 收集顶层枚举定义 */
  private void collectEnum(String rawType) {
    ExcelFieldParseAdapter adapter;
    try {
      adapter = ExcelFieldParseAdapter.getFieldAdapterByTypeStr(rawType);
    } catch (Exception e) {
      // 类型非法时由生成阶段统一报错, 这里只跳过枚举收集
      return;
    }
    if (!(adapter.getFieldAdapter() instanceof ExcelFieldParseAdapter.EnumFieldAdapter)) {
      return;
    }
    ExcelFieldParseAdapter.EnumFieldAdapter enumAdapter =
        (ExcelFieldParseAdapter.EnumFieldAdapter) adapter.getFieldAdapter();
    String enumClassName = enumAdapter.getEnumClassName(rawType);
    Set<String> enumValues = enumAdapter.getEnumFieldSet(rawType);
    enumCache
        .computeIfAbsent(enumClassName, key -> new ConcurrentSkipListSet<>())
        .addAll(enumValues.stream().filter(Objects::nonNull).collect(Collectors.toList()));
  }

  /** 读取常量表 */
  private List<Map<String, String>> loadConstantFields(Workbook workbook) {
    List<Map<String, String>> constants = new ArrayList<>();
    if (workbook == null) {
      return constants;
    }
    String constantSheetName =
        SystemConfigHolder.getInstance().getExcelConf().getDataConstantSheetName();
    int sheetIndex = workbook.getSheetIndex(constantSheetName);
    if (sheetIndex < 0) {
      return constants;
    }
    Sheet sheet = workbook.getSheetAt(sheetIndex);
    int typeCol = SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldTypeCol();
    int nameCol = SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldNameCol();
    int valueCol = SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldValRowRow();
    for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
      String name = cellText(sheet, rowIndex, nameCol);
      String rawType = cellText(sheet, rowIndex, typeCol);
      String valueText = cellText(sheet, rowIndex, valueCol);
      if (name.isEmpty() || rawType.isEmpty()) {
        continue;
      }
      Object value = valueText.isEmpty() ? null : parseValue(rawType, valueText, constantSheetName);
      Map<String, String> constant = new LinkedHashMap<>();
      constant.put("name", name);
      constant.put("literal", toJsLiteral(value));
      constants.add(constant);
    }
    return constants;
  }

  /** 解析配置数据 */
  private Object parseValue(String rawType, String text, String sheetName) {
    ExcelFieldParseAdapter adapter = ExcelFieldParseAdapter.getFieldAdapterByTypeStr(rawType);
    return adapter.getFieldAdapter().parseFiledStrToJavaClassType(text, rawType);
  }

  /** 获取容器绑定的excel文件列表 */
  private List<String> getBindExcelList(
      File file, ExcelFileStructure excelFileStructure, boolean isParentNode) {
    Collection<File> childExcel =
        isParentNode ? excelFileStructure.getChildFileSet() : Collections.singletonList(file);
    String excelConfigLoadPath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath();
    boolean keepRelativePath = getJavaTemplateConf().isKeepBindExcelRelativePath();
    return childExcel.stream()
        .map(
            bindFile -> {
              if (keepRelativePath) {
                return bindFile
                    .getAbsolutePath()
                    .replace(excelConfigLoadPath + File.separator, "")
                    .replace(File.separator, "/");
              }
              return bindFile.getName();
            })
        .sorted(String::compareTo)
        .collect(Collectors.toList());
  }

  /** 配置表中的类型 -> javascript中的类型 */
  private static String toJsType(String rawType) {
    String type = rawType == null ? "" : rawType.trim().toLowerCase();
    if (type.startsWith("date<")) {
      return "Date";
    }
    if (type.startsWith("list<") || type.startsWith("set<") || type.startsWith("map<")) {
      return "Array|Map";
    }
    if (type.contains("bool")) {
      return "boolean";
    }
    if (type.contains("string")) {
      return "string";
    }
    if (type.contains("int")
        || type.contains("byte")
        || type.contains("short")
        || type.contains("long")
        || type.contains("float")
        || type.contains("double")) {
      return "number";
    }
    // 枚举
    return "string";
  }

  /** 转换为JS字面量 */
  private static String toJsLiteral(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Boolean || value instanceof Number) {
      return value.toString();
    }
    if (value instanceof Date) {
      return quote(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value));
    }
    if (value instanceof Map<?, ?> map) {
      StringBuilder builder = new StringBuilder("{ ");
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) {
          builder.append(", ");
        }
        first = false;
        builder
            .append("[")
            .append(toJsLiteral(entry.getKey()))
            .append("]: ")
            .append(toJsLiteral(entry.getValue()));
      }
      return builder.append(" }").toString();
    }
    if (value instanceof Collection<?> collection) {
      StringBuilder builder = new StringBuilder("[ ");
      boolean first = true;
      for (Object item : collection) {
        if (!first) {
          builder.append(", ");
        }
        first = false;
        builder.append(toJsLiteral(item));
      }
      return builder.append(" ]").toString();
    }
    return quote(value.toString());
  }

  /** 字符串转义 */
  private static String quote(String value) {
    return "\"" + escapeText(value) + "\"";
  }

  /** 转义字符串中的特殊字符 */
  private static String escapeText(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }

  private static String cellText(Sheet sheet, int rowIndex, int colIndex) {
    Row row = sheet.getRow(rowIndex);
    if (row == null) {
      return "";
    }
    Cell cell = row.getCell(colIndex);
    if (cell == null) {
      return "";
    }
    return ExcelUtils.getCellValue(cell).trim();
  }

  private String getCfgBeanClassName(String sheetName) {
    String suffix = "cfg";
    String className = sheetName;
    if (!className.toUpperCase().endsWith(suffix.toUpperCase())) {
      className = className + Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1);
    }
    return Character.toUpperCase(className.charAt(0)) + className.substring(1);
  }

  private static String toLowerFirst(String name) {
    if (StringUtils.isEmpty(name)) {
      return name;
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private String getGenerateDate() {
    return new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss").format(new Date());
  }

  private static String getTargetDir() {
    return SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
        + File.separator;
  }

  private static JavaTemplateConf getJavaTemplateConf() {
    return SystemConfigHolder.getInstance().getJavaTemplateConf();
  }

  /** 容器定义 */
  public static class ContainerDef {
    private final String className;
    private final String fieldName;
    private final String moduleName;

    public ContainerDef(String className, String fieldName, String moduleName) {
      this.className = className;
      this.fieldName = fieldName;
      this.moduleName = moduleName;
    }

    public String getClassName() {
      return className;
    }

    public String getFieldName() {
      return fieldName;
    }

    public String getModuleName() {
      return moduleName;
    }
  }

  /** 枚举定义 */
  public static class EnumDef {
    private final String name;
    private final List<String> values;

    public EnumDef(String name, List<String> values) {
      this.name = name;
      this.values = values;
    }

    public String getName() {
      return name;
    }

    public List<String> getValues() {
      return values;
    }
  }

  public static JsTemplateGenerator getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final JsTemplateGenerator instance;

    Singleton() {
      this.instance = new JsTemplateGenerator();
    }

    public JsTemplateGenerator getInstance() {
      return instance;
    }
  }
}
