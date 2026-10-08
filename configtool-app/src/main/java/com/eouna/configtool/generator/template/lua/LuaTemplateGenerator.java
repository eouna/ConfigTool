package com.eouna.configtool.generator.template.lua;

import com.eouna.configtool.configholder.ConfigDataBean.JavaTemplateConf;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Lua模板生成器。
 *
 * <p>Lua 运行期没有xlsx解析库, 因此数据在生成期由工具解析excel后直接写成Lua table(纯数据模块), 运行期零依赖。
 *
 * @author CCL
 */
public class LuaTemplateGenerator extends AbstractTemplateGenerator {

  private static final String LUA_DIR = "lua";

  /** 枚举(枚举名 -> 值) */
  private final Map<String, Set<String>> enumCache = new ConcurrentSkipListMap<>();
  /** 容器定义 */
  private final Map<String, ContainerDef> containerDefMap = new ConcurrentSkipListMap<>();
  /** 已生成bean */
  private final Set<String> generatedBeanSet = ConcurrentHashMap.newKeySet();

  @Override
  protected String getTemplateBindRelatedPath() {
    return ETemplateGenerator.LUA_GENERATOR.getTemplateHandler().getTemplateBindRelatedPath();
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
      FileUtils.getOrCreateDir(getTargetDir() + LUA_DIR);
    } catch (Exception e) {
      LoggerUtils.getLogger().error("初始化Lua输出目录异常", e);
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
    if (sheet == null) {
      return;
    }
    String beanClassName = getCfgBeanClassName(sheet.getSheetName());
    if (!generatedBeanSet.add(beanClassName)) {
      throw new ExcelParseException("生成Lua Bean失败, 存在重复的工作薄名: " + sheet.getSheetName());
    }
    String beanModule = "bean_" + toSnakeCase(beanClassName);
    String dataModule = "data_" + toSnakeCase(beanClassName);
    String constantsModule = "constants_" + toSnakeCase(beanClassName);
    String containerModule = "container_" + toSnakeCase(beanClassName);
    String containerClassName = beanClassName + "Container";

    int nameRow = 2;
    int typeRow = 1;
    int descRow = 0;
    int dataStartRow = 4;
    int lastCol = sheet.getRow(nameRow) == null ? 0 : sheet.getRow(nameRow).getLastCellNum();

    String idName = getJavaTemplateConf().getBaseBeanIdName();
    List<Map<String, String>> fields = new ArrayList<>();
    List<String> rawTypes = new ArrayList<>();
    for (int col = 0; col < lastCol; col++) {
      String name = cellText(sheet, nameRow, col);
      String rawType = cellText(sheet, typeRow, col);
      String desc = cellText(sheet, descRow, col);
      if (name.isEmpty() || rawType.isEmpty()) {
        continue;
      }
      Map<String, String> field = new LinkedHashMap<>();
      field.put("name", name);
      field.put("rawType", rawType);
      field.put("type", toLuaType(rawType));
      field.put("desc", desc);
      fields.add(field);
      rawTypes.add(rawType);
    }
    if (fields.isEmpty()) {
      throw new ExcelParseException("Lua配置表没有可用字段: " + sheet.getSheetName());
    }

    // 数据
    StringBuilder dataBody = new StringBuilder();
    for (int rowIndex = dataStartRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
      List<String> parts = new ArrayList<>();
      Object idValue = null;
      for (int i = 0; i < fields.size(); i++) {
        Map<String, String> field = fields.get(i);
        String text = cellText(sheet, rowIndex, i);
        if (text.isEmpty()) {
          continue;
        }
        Object value = parseValue(rawTypes.get(i), text, sheet.getSheetName());
        if (idName.equalsIgnoreCase(field.get("name"))) {
          idValue = value;
        }
        parts.add(field.get("name") + " = " + toLuaLiteral(value));
      }
      if (idValue == null || parts.isEmpty()) {
        continue;
      }
      dataBody
          .append("    [")
          .append(toLuaLiteral(idValue))
          .append("] = { ")
          .append(String.join(", ", parts))
          .append(" },\n");
    }

    Map<String, Object> beanDataMap = new HashMap<>(8);
    beanDataMap.put("beanClassName", beanClassName);
    beanDataMap.put("excelName", file.getName());
    beanDataMap.put("sheetName", sheet.getSheetName());
    beanDataMap.put("idName", idName);
    beanDataMap.put("fields", fields);
    beanDataMap.put("date", getGenerateDate());
    generateTemplate(beanDataMap, "Bean.ftl", getTargetDir() + LUA_DIR + File.separator + beanModule + ".lua");

    Map<String, Object> dataDataMap = new HashMap<>(8);
    dataDataMap.put("beanClassName", beanClassName);
    dataDataMap.put("excelName", file.getName());
    dataDataMap.put("sheetName", sheet.getSheetName());
    dataDataMap.put("date", getGenerateDate());
    dataDataMap.put("body", dataBody.toString());
    generateTemplate(dataDataMap, "Data.ftl", getTargetDir() + LUA_DIR + File.separator + dataModule + ".lua");

    // 常量
    List<Map<String, String>> constantFields = loadConstantFields(workbook);
    boolean hasConstants = !constantFields.isEmpty();
    if (hasConstants) {
      StringBuilder constantBody = new StringBuilder();
      for (Map<String, String> constant : constantFields) {
        constantBody
            .append("    ")
            .append(constant.get("name"))
            .append(" = ")
            .append(constant.get("literal"))
            .append(",\n");
      }
      Map<String, Object> constantDataMap = new HashMap<>(4);
      constantDataMap.put("beanClassName", beanClassName);
      constantDataMap.put("date", getGenerateDate());
      constantDataMap.put("body", constantBody.toString());
      generateTemplate(
          constantDataMap, "Constants.ftl", getTargetDir() + LUA_DIR + File.separator + constantsModule + ".lua");
    }

    Map<String, Object> containerDataMap = new HashMap<>(8);
    containerDataMap.put("containerClassName", containerClassName);
    containerDataMap.put("sheetName", sheet.getSheetName());
    containerDataMap.put("beanModule", beanModule);
    containerDataMap.put("dataModule", dataModule);
    containerDataMap.put("constantsModule", constantsModule);
    containerDataMap.put("hasConstants", hasConstants);
    containerDataMap.put("date", getGenerateDate());
    generateTemplate(
        containerDataMap,
        "Container.ftl",
        getTargetDir() + LUA_DIR + File.separator + containerModule + ".lua");

    containerDefMap.put(
        containerClassName,
        new ContainerDef(containerClassName, toLowerCamel(containerClassName), containerModule));
  }

  @Override
  public void generatorAfter(
      TextAreaLogger textAreaLogger, Map<File, ExcelFileStructure> excelFileStructureMap) {
    try {
      JavaTemplateConf templateConf = getJavaTemplateConf();
      String targetDir = getTargetDir();
      List<ContainerDef> containerDefs = new ArrayList<>(containerDefMap.values());

      if (!enumCache.isEmpty()) {
        List<EnumDef> enumDefs =
            enumCache.entrySet().stream()
                .map(entry -> new EnumDef(entry.getKey(), new ArrayList<>(entry.getValue())))
                .collect(Collectors.toList());
        Map<String, Object> enumDataMap = new HashMap<>(4);
        enumDataMap.put("enumDefs", enumDefs);
        enumDataMap.put("date", getGenerateDate());
        generateTemplate(enumDataMap, "Enums.ftl", targetDir + LUA_DIR + File.separator + "enums.lua");
      }

      Map<String, Object> managerDataMap = new HashMap<>(8);
      managerDataMap.put("containerDefs", containerDefs);
      managerDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      managerDataMap.put("loadMethodName", templateConf.getDataManagerLoadDataCaller());
      managerDataMap.put("date", getGenerateDate());
      generateTemplate(
          managerDataMap, "GameDataManager.ftl", targetDir + LUA_DIR + File.separator + "game_data_manager.lua");

      Map<String, Object> debugDataMap = new HashMap<>(8);
      debugDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      debugDataMap.put("dataManagerModule", "game_data_manager");
      debugDataMap.put("loadMethodName", templateConf.getDataManagerLoadDataCaller());
      debugDataMap.put("excelLoadDir", "");
      debugDataMap.put("containerDefs", containerDefs);
      debugDataMap.put("date", getGenerateDate());
      generateTemplate(debugDataMap, "DebugMain.ftl", targetDir + LUA_DIR + File.separator + "main.lua");

      if (textAreaLogger != null) {
        textAreaLogger.info("生成Lua模板文件结束");
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成Lua管理器/枚举时发生异常", e);
    } finally {
      generatedBeanSet.clear();
    }
  }

  /** 读取常量表, 并把值直接解析为Lua字面量 */
  private List<Map<String, String>> loadConstantFields(Workbook workbook) {
    List<Map<String, String>> constants = new ArrayList<>();
    if (workbook == null) {
      return constants;
    }
    String constantSheetName = SystemConfigHolder.getInstance().getExcelConf().getDataConstantSheetName();
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
      constant.put("literal", toLuaLiteral(value));
      constants.add(constant);
    }
    return constants;
  }

  private Object parseValue(String rawType, String text, String sheetName) {
    ExcelFieldParseAdapter adapter = ExcelFieldParseAdapter.getFieldAdapterByTypeStr(rawType);
    if (adapter.getFieldAdapter() instanceof ExcelFieldParseAdapter.EnumFieldAdapter) {
      // 枚举解析时收集枚举定义
      ExcelFieldParseAdapter.EnumFieldAdapter enumAdapter =
          (ExcelFieldParseAdapter.EnumFieldAdapter) adapter.getFieldAdapter();
      enumCache
          .computeIfAbsent(enumAdapter.getEnumClassName(rawType), key -> new ConcurrentSkipListSet<>())
          .addAll(enumAdapter.getEnumFieldSet(rawType));
    }
    return adapter.getFieldAdapter().parseFiledStrToJavaClassType(text, rawType);
  }

  private static String toLuaType(String rawType) {
    String type = rawType == null ? "" : rawType.trim().toLowerCase();
    if (type.startsWith("date<")) {
      return "string";
    }
    if (type.startsWith("list<") || type.startsWith("set<") || type.startsWith("map<")) {
      return "table";
    }
    if (type.contains("bool")) {
      return "boolean";
    }
    if (type.equals("string") || type.contains("string")) {
      return "string";
    }
    if (type.contains("int") || type.contains("byte") || type.contains("short")
        || type.contains("long") || type.contains("float") || type.contains("double")) {
      return "number";
    }
    // 枚举
    return "string";
  }

  private static String toLuaLiteral(Object value) {
    if (value == null) {
      return "nil";
    }
    if (value instanceof Boolean) {
      return String.valueOf(value);
    }
    if (value instanceof Number) {
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
            .append(toLuaLiteral(entry.getKey()))
            .append("] = ")
            .append(toLuaLiteral(entry.getValue()));
      }
      return builder.append(" }").toString();
    }
    if (value instanceof Collection<?> collection) {
      StringBuilder builder = new StringBuilder("{ ");
      boolean first = true;
      for (Object item : collection) {
        if (!first) {
          builder.append(", ");
        }
        first = false;
        builder.append(toLuaLiteral(item));
      }
      return builder.append(" }").toString();
    }
    return quote(value.toString());
  }

  private static String quote(String value) {
    String escaped =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    return "\"" + escaped + "\"";
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

  private static String toSnakeCase(String name) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          builder.append('_');
        }
        builder.append(Character.toLowerCase(c));
      } else {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  private static String toLowerCamel(String name) {
    if (name.isEmpty()) {
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

  public static LuaTemplateGenerator getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final LuaTemplateGenerator instance;

    Singleton() {
      this.instance = new LuaTemplateGenerator();
    }

    public LuaTemplateGenerator getInstance() {
      return instance;
    }
  }
}