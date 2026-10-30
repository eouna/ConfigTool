package com.eouna.configtool.generator.template.ts;

import com.eouna.configtool.configholder.ConfigDataBean.JavaTemplateConf;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.generator.base.ExcelFileStructure;
import com.eouna.configtool.generator.bean.ExcelSheetBean;
import com.eouna.configtool.generator.exceptions.ExcelFormatCheckException;
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
import java.util.Locale;
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
 * TypeScript模板生成器。
 *
 * <p>TypeScript运行期通过JSON加载器读取工具导出的数据, 与JavaScript模板共用同一份JSON。生成的 Bean字段、容器和管理器均带明确类型, 工程可直接通过
 * {@code npm run build} 编译。
 *
 * <pre>
 * ts/
 * ├── package.json
 * ├── tsconfig.json
 * └── src/
 *     ├── JsonLoader.ts
 *     ├── TypeConverters.ts
 *     ├── Types.ts
 *     ├── NodeTypes.d.ts
 *     ├── Enums.ts
 *     ├── constants_xxx.ts
 *     ├── bean/xxxCfg.ts
 *     ├── container/BaseCfgContainer.ts
 *     ├── container/xxxCfgContainer.ts
 *     ├── GameDataManager.ts
 *     └── debug/main.ts
 * </pre>
 *
 * @author CCL
 */
public class TsTemplateGenerator extends AbstractTemplateGenerator {

  /** 输出根目录 */
  private static final String TS_DIR = "ts";

  /** TypeScript源码目录 */
  private static final String SRC_DIR = TS_DIR + File.separator + "src";

  /** Bean目录 */
  private static final String BEAN_DIR =
      SRC_DIR + File.separator + DefaultEnvConfigConstant.CFG_BEAN_PATH;

  /** 容器目录 */
  private static final String CONTAINER_DIR =
      SRC_DIR + File.separator + DefaultEnvConfigConstant.CONTAINER_PATH;

  /** 调试入口目录 */
  private static final String DEBUG_DIR = SRC_DIR + File.separator + "debug";

  /** 枚举(枚举名 -> 值) */
  private final Map<String, Set<String>> enumCache = new ConcurrentSkipListMap<>();

  /** 容器定义 */
  private final Map<String, ContainerDef> containerDefMap = new ConcurrentSkipListMap<>();

  /** 已生成Bean, 用于检查重复工作薄名 */
  private final Set<String> generatedBeanSet = ConcurrentHashMap.newKeySet();

  @Override
  protected String getTemplateBindRelatedPath() {
    return ETemplateGenerator.TS_GENERATOR.getTemplateHandler().getTemplateBindRelatedPath();
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
      FileUtils.getOrCreateDir(getTargetDir() + TS_DIR);
      FileUtils.getOrCreateDir(getTargetDir() + SRC_DIR);
      FileUtils.getOrCreateDir(getTargetDir() + BEAN_DIR);
      FileUtils.getOrCreateDir(getTargetDir() + CONTAINER_DIR);
      FileUtils.getOrCreateDir(getTargetDir() + DEBUG_DIR);

      String date = getGenerateDate();
      Map<String, Object> commonDataMap = new HashMap<>(4);
      commonDataMap.put("date", date);
      commonDataMap.put(
          "isSplitMultiJson",
          SystemConfigHolder.getInstance().getJsonTemplateConf().isSplitMultiJson());

      generateTemplate(
          commonDataMap,
          "PackageJson.ftl",
          getTargetDir() + TS_DIR + File.separator + "package.json");
      generateTemplate(
          commonDataMap,
          "TsConfig.ftl",
          getTargetDir() + TS_DIR + File.separator + "tsconfig.json");
      generateTemplate(
          commonDataMap,
          "NodeTypes.ftl",
          getTargetDir() + SRC_DIR + File.separator + "NodeTypes.d.ts");
      generateTemplate(
          commonDataMap, "Types.ftl", getTargetDir() + SRC_DIR + File.separator + "Types.ts");
      generateTemplate(
          commonDataMap,
          "TypeConverters.ftl",
          getTargetDir() + SRC_DIR + File.separator + "TypeConverters.ts");
      generateTemplate(
          commonDataMap,
          "JsonLoader.ftl",
          getTargetDir() + SRC_DIR + File.separator + "JsonLoader.ts");
      generateTemplate(
          commonDataMap,
          "BaseCfgContainer.ftl",
          getTargetDir() + CONTAINER_DIR + File.separator + "BaseCfgContainer.ts");

      // 分表的父节点是虚拟文件, 需要额外生成父级Bean与容器
      Set<String> generatedParentList = new HashSet<>();
      for (Map.Entry<File, ExcelFileStructure> entry : excelFileStructureMap.entrySet()) {
        File currentDealFile = entry.getKey();
        if (currentDealFile.exists() || !generatedParentList.add(currentDealFile.getName())) {
          continue;
        }
        ExcelSheetBean parentSheetBean =
            new ExcelSheetBean(currentDealFile, currentDealFile.getName());
        generatorOneExcelFile(currentDealFile, null, null, parentSheetBean, entry.getValue());
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("初始化TypeScript输出目录异常", e);
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
    boolean isParentNode = sheet == null;
    String sheetName = sheetBean.getSheetName();
    String beanClassName = getCfgBeanClassName(sheetName);
    if (!generatedBeanSet.add(beanClassName)) {
      throw new ExcelParseException("生成TypeScript Bean失败, 存在重复的工作薄名: " + sheetName);
    }
    String beanModule = toLowerFirst(beanClassName);
    String containerClassName = beanClassName + DefaultEnvConfigConstant.CONTAINER_GEN_SUFFIX;
    String containerModule = toLowerFirst(containerClassName);
    String constantsClassName = beanClassName + "Constants";
    String constantsModule = "constants_" + beanModule;

    List<Map<String, String>> fields = loadFields(file, sheet, excelFileStructure, isParentNode);
    if (fields.isEmpty()) {
      throw new ExcelParseException("TypeScript配置表没有可用字段: " + sheetName);
    }

    Map<String, Object> beanDataMap = new HashMap<>(8);
    beanDataMap.put("beanClassName", beanClassName);
    beanDataMap.put("excelName", file.getName());
    beanDataMap.put("sheetName", sheetName);
    beanDataMap.put("idName", getJavaTemplateConf().getBaseBeanIdName());
    beanDataMap.put("fields", fields);
    beanDataMap.put("date", getGenerateDate());
    generateTemplate(
        beanDataMap, "Bean.ftl", getTargetDir() + BEAN_DIR + File.separator + beanModule + ".ts");

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
          getTargetDir() + SRC_DIR + File.separator + constantsModule + ".ts");
    }

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
        getTargetDir() + CONTAINER_DIR + File.separator + containerModule + ".ts");

    containerDefMap.put(
        containerClassName,
        new ContainerDef(containerClassName, toLowerFirst(containerClassName), containerModule));
  }

  @Override
  public void generatorAfter(
      TextAreaLogger textAreaLogger, Map<File, ExcelFileStructure> excelFileStructureMap) {
    try {
      String targetDir = getTargetDir();
      String date = getGenerateDate();
      Collection<ContainerDef> containerDefs = containerDefMap.values();

      List<EnumDef> enumDefs = new ArrayList<>();
      enumCache.forEach((name, values) -> enumDefs.add(new EnumDef(name, new ArrayList<>(values))));
      Map<String, Object> enumDataMap = new HashMap<>(4);
      enumDataMap.put("enumDefs", enumDefs);
      enumDataMap.put("date", date);
      generateTemplate(enumDataMap, "Enums.ftl", targetDir + SRC_DIR + File.separator + "Enums.ts");

      JavaTemplateConf templateConf = getJavaTemplateConf();
      Map<String, Object> managerDataMap = new HashMap<>(8);
      managerDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      managerDataMap.put("containerDefs", containerDefs);
      managerDataMap.put("loadMethodName", templateConf.getDataManagerLoadDataCaller());
      managerDataMap.put("date", date);
      generateTemplate(
          managerDataMap,
          "GameDataManager.ftl",
          targetDir + SRC_DIR + File.separator + "GameDataManager.ts");

      if (isExportMainEntry()) {
        Map<String, Object> debugDataMap = new HashMap<>(8);
        debugDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
        debugDataMap.put("containerDefs", containerDefs);
        debugDataMap.put("loadMethodName", templateConf.getDataManagerLoadDataCaller());
        debugDataMap.put("date", date);
        debugDataMap.put("printLoadData", isPrintLoadData());
        debugDataMap.put(
            "isSplitMultiJson",
            SystemConfigHolder.getInstance().getJsonTemplateConf().isSplitMultiJson());
        generateTemplate(
            debugDataMap, "DebugMain.ftl", targetDir + DEBUG_DIR + File.separator + "main.ts");
      }

      if (textAreaLogger != null) {
        textAreaLogger.info("生成TypeScript模板文件结束(数据源: json)");
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成TypeScript管理器/枚举时发生异常", e);
      // 抛出让统一生成流程把异常反馈给界面, 避免只写日志却显示生成成功
      throw new RuntimeException(e);
    } finally {
      generatedBeanSet.clear();
    }
  }

  /** 读取表头字段元数据, 不解析数据行。 */
  private List<Map<String, String>> loadFields(
      File file, Sheet sheet, ExcelFileStructure excelFileStructure, boolean isParentNode)
      throws Exception {
    int descRow = SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldDescRow();
    int typeRow = SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldTypeRow();
    int nameRow = SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldNameRow();
    String clientSkipStr = SystemConfigHolder.getInstance().getExportSkipStr();

    Collection<File> excelFileList =
        isParentNode ? excelFileStructure.getChildFileSet() : Collections.singletonList(file);

    List<Map<String, String>> fields = new ArrayList<>();
    Set<String> generatedFieldNames = new HashSet<>();
    for (File excelFile : excelFileList) {
      try (Workbook workbook = WorkbookFactory.create(excelFile, null, true)) {
        Sheet currentSheet = workbook.getSheetAt(0);
        Set<Integer> skipColList =
            ExcelTemplateGenUtils.getSkipCellList(currentSheet, clientSkipStr);
        int lastCol =
            currentSheet.getRow(nameRow) == null
                ? 0
                : currentSheet.getRow(nameRow).getLastCellNum();
        for (int col = 0; col < lastCol; col++) {
          if (skipColList.contains(col)) {
            continue;
          }
          String name = cellText(currentSheet, nameRow, col);
          String rawType = cellText(currentSheet, typeRow, col);
          if (name.isEmpty() || rawType.isEmpty() || !generatedFieldNames.add(name)) {
            continue;
          }
          collectEnum(rawType);
          Map<String, String> field = new LinkedHashMap<>();
          field.put("name", escapeText(name));
          field.put("propertyName", toTsPropertyName(name));
          field.put("rawType", escapeText(rawType));
          field.put("tsType", toTsType(rawType));
          field.put("initializer", toTsConversionExpression(rawType, "raw[" + quote(name) + "]"));
          field.put("desc", escapeText(cellText(currentSheet, descRow, col)));
          fields.add(field);
        }
      }
    }
    return fields;
  }

  /** 收集顶层枚举定义。 */
  private void collectEnum(String rawType) {
    ExcelFieldParseAdapter adapter;
    try {
      adapter = ExcelFieldParseAdapter.getFieldAdapterByTypeStr(rawType);
    } catch (Exception e) {
      return;
    }
    if (!(adapter.getFieldAdapter()
        instanceof ExcelFieldParseAdapter.EnumFieldAdapter enumAdapter)) {
      return;
    }
    String enumClassName = enumAdapter.getEnumClassName(rawType);
    Set<String> enumValues = enumAdapter.getEnumFieldSet(rawType);
    enumCache
        .computeIfAbsent(enumClassName, key -> new ConcurrentSkipListSet<>())
        .addAll(enumValues.stream().filter(Objects::nonNull).collect(Collectors.toList()));
  }

  /** 读取常量表。 */
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
    int typeCol =
        SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldTypeCol();
    int nameCol =
        SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldNameCol();
    int valueCol =
        SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldValRowRow();
    for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
      String name = cellText(sheet, rowIndex, nameCol);
      String rawType = cellText(sheet, rowIndex, typeCol);
      String valueText = cellText(sheet, rowIndex, valueCol);
      if (name.isEmpty() || rawType.isEmpty()) {
        continue;
      }
      Object value = valueText.isEmpty() ? null : parseValue(rawType, valueText);
      Map<String, String> constant = new LinkedHashMap<>();
      constant.put("name", name);
      constant.put("literal", toTsLiteral(value));
      constants.add(constant);
    }
    return constants;
  }

  private Object parseValue(String rawType, String text) {
    ExcelFieldParseAdapter adapter = ExcelFieldParseAdapter.getFieldAdapterByTypeStr(rawType);
    return adapter.getFieldAdapter().parseFiledStrToJavaClassType(text, rawType);
  }

  /** 获取容器绑定的Excel文件列表。 */
  private List<String> getBindExcelList(
      File file, ExcelFileStructure excelFileStructure, boolean isParentNode) {
    Collection<File> childExcel =
        isParentNode ? excelFileStructure.getChildFileSet() : Collections.singletonList(file);
    String excelConfigLoadPath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath();
    boolean keepRelativePath = getJavaTemplateConf().isKeepBindExcelRelativePath();
    return childExcel.stream()
        .map(
            bindFile ->
                keepRelativePath
                    ? bindFile
                        .getAbsolutePath()
                        .replace(excelConfigLoadPath + File.separator, "")
                        .replace(File.separator, "/")
                    : bindFile.getName())
        .sorted(String::compareTo)
        .collect(Collectors.toList());
  }

  /** 配置表字段类型 -> TypeScript类型。 */
  private static String toTsType(String rawType) {
    return TypeSpecParser.parse(rawType).toTsType();
  }

  /** 配置表字段类型 -> JSON值到TypeScript值的递归转换表达式。 */
  private static String toTsConversionExpression(String rawType, String valueExpression) {
    return TypeSpecParser.parse(rawType).toConversionExpression(valueExpression);
  }

  /** 转换为TypeScript字面量。 */
  private static String toTsLiteral(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Boolean || value instanceof Number) {
      return value.toString();
    }
    if (value instanceof Date date) {
      return quote(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date));
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
            .append(toTsLiteral(entry.getKey()))
            .append("]: ")
            .append(toTsLiteral(entry.getValue()));
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
        builder.append(toTsLiteral(item));
      }
      return builder.append(" ]").toString();
    }
    return quote(value.toString());
  }

  private static String quote(String value) {
    return "\"" + escapeText(value) + "\"";
  }

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

  private static String toTsPropertyName(String fieldName) {
    if (fieldName != null && fieldName.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
      return fieldName;
    }
    return quote(fieldName);
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
    if (!className.toUpperCase(Locale.ROOT).endsWith(suffix.toUpperCase(Locale.ROOT))) {
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

  /** 解析配置表类型文法, 同时生成TS类型和JSON值转换表达式。 */
  private static final class TypeSpecParser {
    private final String source;
    private int position;

    private TypeSpecParser(String source) {
      this.source = source == null ? "" : source.trim();
    }

    static TypeSpec parse(String rawType) {
      TypeSpecParser parser = new TypeSpecParser(rawType);
      TypeSpec result = parser.parseType();
      parser.skipWhitespace();
      if (!parser.isEnd()) {
        throw new ExcelFormatCheckException("无法解析TypeScript字段类型: " + rawType);
      }
      return result;
    }

    private TypeSpec parseType() {
      skipWhitespace();
      if (isEnd()) {
        throw new ExcelFormatCheckException("TypeScript字段类型不能为空");
      }
      if (startsWithIgnoreCase("date<")) {
        int end = source.indexOf('>', position);
        if (end < 0) {
          throw new ExcelFormatCheckException("日期字段类型缺少结束符: " + source);
        }
        position = end + 1;
        return TypeSpec.primitive("date", "string", "asString");
      }
      if (consumeWordAndOpen("list")) {
        TypeSpec element = parseType();
        expect('>');
        skipMetadata();
        return TypeSpec.list(element);
      }
      if (consumeWordAndOpen("set")) {
        TypeSpec element = parseType();
        expect('>');
        skipMetadata();
        return TypeSpec.set(element);
      }
      if (consumeWordAndOpen("map")) {
        TypeSpec key = parseType();
        skipMetadata();
        TypeSpec value = parseType();
        expect('>');
        skipMetadata();
        return TypeSpec.map(key, value);
      }

      int start = position;
      int parenDepth = 0;
      while (position < source.length()) {
        char current = source.charAt(position);
        if (current == '(') {
          parenDepth++;
        } else if (current == ')' && parenDepth > 0) {
          parenDepth--;
        } else if (parenDepth == 0
            && (current == '<'
                || current == '>'
                || current == ','
                || current == '{'
                || current == '}')) {
          break;
        }
        position++;
      }
      String token = source.substring(start, position).trim();
      if (token.isEmpty()) {
        throw new ExcelFormatCheckException("无法解析TypeScript字段类型: " + source);
      }
      String lower = token.toLowerCase(Locale.ROOT);
      if (lower.contains("bool")) {
        return TypeSpec.primitive(token, "boolean", "asBoolean");
      }
      if (lower.contains("string")) {
        return TypeSpec.primitive(token, "string", "asString");
      }
      if (lower.contains("byte")
          || lower.contains("short")
          || lower.contains("int")
          || lower.contains("long")
          || lower.contains("float")
          || lower.contains("double")) {
        return TypeSpec.primitive(token, "number", "asNumber");
      }
      // 枚举与无法识别的自定义值都按字符串处理
      return TypeSpec.primitive(token, "string", "asString");
    }

    private boolean consumeWordAndOpen(String word) {
      if (!startsWithIgnoreCase(word)) {
        return false;
      }
      int afterWord = position + word.length();
      while (afterWord < source.length() && Character.isWhitespace(source.charAt(afterWord))) {
        afterWord++;
      }
      if (afterWord >= source.length() || source.charAt(afterWord) != '<') {
        return false;
      }
      position = afterWord + 1;
      return true;
    }

    private boolean startsWithIgnoreCase(String text) {
      return source.regionMatches(true, position, text, 0, text.length());
    }

    private void expect(char expected) {
      skipWhitespace();
      if (isEnd() || source.charAt(position) != expected) {
        throw new ExcelFormatCheckException("TypeScript字段类型缺少 '" + expected + "': " + source);
      }
      position++;
    }

    private void skipMetadata() {
      skipWhitespace();
      if (isEnd() || source.charAt(position) != '{') {
        return;
      }
      int end = source.indexOf('}', position);
      if (end < 0) {
        throw new ExcelFormatCheckException("集合字段类型分隔配置缺少结束符: " + source);
      }
      position = end + 1;
    }

    private void skipWhitespace() {
      while (!isEnd() && Character.isWhitespace(source.charAt(position))) {
        position++;
      }
    }

    private boolean isEnd() {
      return position >= source.length();
    }
  }

  /** 解析后的字段类型 */
  private static final class TypeSpec {
    private enum Kind {
      PRIMITIVE,
      LIST,
      SET,
      MAP
    }

    private final Kind kind;
    private final String tsType;
    private final String converter;
    private final TypeSpec first;
    private final TypeSpec second;

    private TypeSpec(Kind kind, String tsType, String converter, TypeSpec first, TypeSpec second) {
      this.kind = kind;
      this.tsType = tsType;
      this.converter = converter;
      this.first = first;
      this.second = second;
    }

    static TypeSpec primitive(String rawType, String tsType, String converter) {
      return new TypeSpec(Kind.PRIMITIVE, tsType, converter, null, null);
    }

    static TypeSpec list(TypeSpec element) {
      return new TypeSpec(Kind.LIST, element.toTsType() + "[]", "toList", element, null);
    }

    static TypeSpec set(TypeSpec element) {
      return new TypeSpec(Kind.SET, "Set<" + element.toTsType() + ">", "toSet", element, null);
    }

    static TypeSpec map(TypeSpec key, TypeSpec value) {
      return new TypeSpec(
          Kind.MAP, "Readonly<Record<string, " + value.toTsType() + ">>", "toMap", key, value);
    }

    String toTsType() {
      return tsType;
    }

    String toConversionExpression(String valueExpression) {
      return switch (kind) {
        case PRIMITIVE -> converter + "(" + valueExpression + ")";
        case LIST, SET ->
            converter
                + "("
                + valueExpression
                + ", item => "
                + first.toConversionExpression("item")
                + ")";
        case MAP ->
            "toMap("
                + valueExpression
                + ", mapValue => "
                + second.toConversionExpression("mapValue")
                + ")";
      };
    }
  }

  public static TsTemplateGenerator getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final TsTemplateGenerator instance;

    Singleton() {
      this.instance = new TsTemplateGenerator();
    }

    public TsTemplateGenerator getInstance() {
      return instance;
    }
  }
}
