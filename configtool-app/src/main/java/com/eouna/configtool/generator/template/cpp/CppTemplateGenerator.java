package com.eouna.configtool.generator.template.cpp;

import com.eouna.configtool.configholder.ConfigDataBean.JavaTemplateConf;
import com.eouna.configtool.configholder.SystemConfigHolder;
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
import com.eouna.configtool.utils.StrUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * C++模板生成器(按Java模板重写: 运行期读取xlsx并解析)。
 *
 * <p>与Java模板对应关系: BaseCfgContainer -&gt; BaseCfgContainer.h, CfgBean -&gt; bean/xxx.h(含内嵌枚举),
 * CfgContainer -&gt; container/xxxContainer.h, GameDataManager -&gt; GameDataManager.h;
 * xlsx读取由随模板输出的 third_party/miniz + XlsxReader.h 完成, 运行期仅依赖标准库。
 *
 * @author CCL
 */
public class CppTemplateGenerator extends AbstractTemplateGenerator {

  private static final String CPP_DIR = "cpp";
  private static final String THIRD_PARTY_DIR = "third_party";

  /** CMakeLists内容(含${}变量, 不走FreeMarker直接写出) */
  private static final String CMAKE_LISTS_CONTENT =
      "cmake_minimum_required(VERSION 3.15)\n"
          + "project(ConfigToolCpp C CXX)\n\n"
          + "set(CMAKE_CXX_STANDARD 17)\n"
          + "set(CMAKE_CXX_STANDARD_REQUIRED ON)\n\n"
          + "if(MSVC)\n"
          + "    add_compile_options(/utf-8 /W4)\n"
          + "else()\n"
          + "    add_compile_options(-Wall -Wextra)\n"
          + "    find_package(Threads REQUIRED)\n"
          + "endif()\n\n"
          + "add_executable(config_tool_cpp main.cpp third_party/miniz.c third_party/miniz_tdef.c third_party/miniz_tinfl.c third_party/miniz_zip.c)\n"
          + "target_include_directories(config_tool_cpp PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include ${CMAKE_CURRENT_SOURCE_DIR}/third_party)\n\n"
          + "if(NOT MSVC)\n"
          + "    target_link_libraries(config_tool_cpp PRIVATE Threads::Threads)\n"
          + "endif()\n";

  /** 容器定义 */
  private final Map<String, ContainerDef> containerDefMap = new ConcurrentSkipListMap<>();
  /** 已生成bean */
  private final Set<String> generatedBeanSet = ConcurrentHashMap.newKeySet();

  @Override
  protected String getTemplateBindRelatedPath() {
    return ETemplateGenerator.CPP_GENERATOR.getTemplateHandler().getTemplateBindRelatedPath();
  }

  @Override
  public void generatorBefore(
      List<File> successGenList,
      Map<File, ExcelFileStructure> excelFileStructureMap,
      List<Exception> exceptionCollector) {
    try {
      containerDefMap.clear();
      generatedBeanSet.clear();
      String baseDir = getTargetDir() + CPP_DIR + File.separator;
      FileUtils.getOrCreateDir(baseDir);
      FileUtils.getOrCreateDir(baseDir + "include");
      FileUtils.getOrCreateDir(baseDir + "include" + File.separator + "bean");
      FileUtils.getOrCreateDir(baseDir + "include" + File.separator + "container");
      FileUtils.getOrCreateDir(baseDir + THIRD_PARTY_DIR);

      // 运行期基础头文件
      Map<String, Object> baseDataMap = new LinkedHashMap<>();
      baseDataMap.put("fieldInfo", new com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelFieldInfo());
      baseDataMap.put("constFieldInfo", new com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelConstantFieldInfo());
      baseDataMap.put("constantSheetName", SystemConfigHolder.getInstance().getExcelConf().getDataConstantSheetName());
      baseDataMap.put("dataStartRow", ExcelTemplateGenUtils.getConfigFieldMaxRow() + 1);
      baseDataMap.put("idName", getJavaTemplateConf().getBaseBeanIdName());
      baseDataMap.put("skipStr", getJavaTemplateConf().getDataRangeServerSkipStr());
      baseDataMap.put("date", getGenerateDate());
      generateTemplate(baseDataMap, "BaseCfgContainer.ftl", baseDir + "include" + File.separator + "container" + File.separator + "BaseCfgContainer.h");

      Map<String, Object> defsDataMap = new LinkedHashMap<>();
      generateTemplate(defsDataMap, "CfgDefs.ftl", baseDir + "include" + File.separator + "CfgDefs.h");
      generateTemplate(defsDataMap, "XlsxReader.ftl", baseDir + "include" + File.separator + "XlsxReader.h");

      // 随模板输出的miniz(离线可用)
      copyThirdParty(baseDir);

      Files.writeString(Paths.get(baseDir + "CMakeLists.txt"), CMAKE_LISTS_CONTENT, StandardCharsets.UTF_8);
    } catch (Exception e) {
      LoggerUtils.getLogger().error("初始化C++输出目录异常", e);
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
      throw new ExcelParseException("生成C++ Bean失败, 存在重复的工作薄名: " + sheet.getSheetName());
    }
    String idName = getJavaTemplateConf().getBaseBeanIdName();

    int descRow = 0;
    int typeRow = 1;
    int nameRow = 2;
    int lastCol = sheet.getRow(nameRow) == null ? 0 : sheet.getRow(nameRow).getLastCellNum();
    String skipStr = getJavaTemplateConf().getDataRangeServerSkipStr();
    Set<Integer> skipCols = ExcelTemplateGenUtils.getSkipCellList(sheet, skipStr);

    List<Map<String, Object>> fields = new ArrayList<>();
    Map<String, Set<String>> enums = new TreeMap<>();
    for (int col = 0; col < lastCol; col++) {
      if (skipCols.contains(col)) {
        continue;
      }
      String name = cellText(sheet, nameRow, col);
      String rawType = cellText(sheet, typeRow, col);
      String desc = cellText(sheet, descRow, col);
      if (name.isEmpty() || rawType.isEmpty() || idName.equalsIgnoreCase(name)) {
        continue;
      }
      boolean enumField = isEnum(rawType);
      String enumName = enumField ? enumNameOf(rawType) : "";
      if (enumField) {
        enums.computeIfAbsent(enumName, key -> new TreeSet<>()).addAll(enumValuesOf(rawType));
      }
      String javaType = toJavaType(rawType);
      Map<String, Object> field = new LinkedHashMap<>();
      field.put("name", name);
      field.put("rawType", rawType);
      field.put("desc", desc);
      field.put("isEnum", enumField);
      field.put("enumName", enumName);
      field.put("type", toCppType(javaType, false, beanClassName));
      field.put("qualifiedType", toCppType(javaType, true, beanClassName));
      fields.add(field);
    }
    if (fields.isEmpty()) {
      throw new ExcelParseException("C++配置表没有可用字段: " + sheet.getSheetName());
    }

    List<EnumDef> enumDefs = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : enums.entrySet()) {
      enumDefs.add(new EnumDef(entry.getKey(), new ArrayList<>(entry.getValue())));
    }

    Map<String, Object> beanDataMap = new LinkedHashMap<>();
    beanDataMap.put("beanClassName", beanClassName);
    beanDataMap.put("excelName", file.getName());
    beanDataMap.put("sheetName", sheet.getSheetName());
    beanDataMap.put("idName", idName);
    beanDataMap.put("fields", fields);
    beanDataMap.put("enumDefs", enumDefs);
    beanDataMap.put("date", getGenerateDate());
    generateTemplate(
        beanDataMap, "Bean.ftl", getTargetDir() + CPP_DIR + File.separator + "include" + File.separator + "bean" + File.separator + beanClassName + ".h");

    List<Map<String, String>> constantFields = loadConstantFields(workbook, beanClassName);
    String containerClassName = beanClassName + "Container";
    Map<String, Object> containerDataMap = new LinkedHashMap<>();
    containerDataMap.put("containerClassName", containerClassName);
    containerDataMap.put("beanClassName", beanClassName);
    containerDataMap.put("excelName", file.getName());
    containerDataMap.put("sheetName", sheet.getSheetName());
    containerDataMap.put("idName", idName);
    containerDataMap.put("fields", fields);
    containerDataMap.put("constantFields", constantFields);
    containerDataMap.put("date", getGenerateDate());
    generateTemplate(
        containerDataMap,
        "Container.ftl",
        getTargetDir() + CPP_DIR + File.separator + "include" + File.separator + "container" + File.separator + containerClassName + ".h");

    containerDefMap.put(
        containerClassName,
        new ContainerDef(containerClassName, "m_" + toLowerCamel(containerClassName), "Get" + containerClassName));
  }

  @Override
  public void generatorAfter(
      TextAreaLogger textAreaLogger, Map<File, ExcelFileStructure> excelFileStructureMap) {
    try {
      JavaTemplateConf templateConf = getJavaTemplateConf();
      String baseDir = getTargetDir() + CPP_DIR + File.separator;
      List<ContainerDef> containerDefs = new ArrayList<>(containerDefMap.values());

      Map<String, Object> managerDataMap = new LinkedHashMap<>();
      managerDataMap.put("containerDefs", containerDefs);
      managerDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      managerDataMap.put("loadMethodName", StrUtils.upperFirst(templateConf.getDataManagerLoadDataCaller()));
      managerDataMap.put("date", getGenerateDate());
      generateTemplate(managerDataMap, "GameDataManager.ftl", baseDir + "include" + File.separator + "GameDataManager.h");

      Map<String, Object> mainDataMap = new LinkedHashMap<>();
      mainDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      mainDataMap.put("loadMethodName", StrUtils.upperFirst(templateConf.getDataManagerLoadDataCaller()));
      mainDataMap.put(
          "excelLoadDir",
          SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath().replace("\\", "\\\\"));
      mainDataMap.put("containerDefs", containerDefs);
      mainDataMap.put("date", getGenerateDate());
      generateTemplate(mainDataMap, "Main.ftl", baseDir + "main.cpp");

      if (textAreaLogger != null) {
        textAreaLogger.info("生成C++模板文件结束");
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成C++管理器时发生异常", e);
    } finally {
      generatedBeanSet.clear();
    }
  }

  /** 复制随模板输出的第三方库(miniz) */
  private void copyThirdParty(String baseDir) throws Exception {
    String templatePath = SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplatePath();
    String sourceDir = templatePath + File.separator + CPP_DIR + File.separator + THIRD_PARTY_DIR + File.separator;
    for (String fileName : new String[] {"miniz.h", "miniz.c", "miniz_export.h", "miniz_common.h", "miniz_tdef.h", "miniz_tdef.c", "miniz_tinfl.h", "miniz_tinfl.c", "miniz_zip.h", "miniz_zip.c"}) {
      File source = new File(sourceDir + fileName);
      if (!source.exists()) {
        throw new ExcelParseException("缺少第三方依赖文件: " + source.getPath());
      }
      Files.copy(
          source.toPath(),
          Paths.get(baseDir + THIRD_PARTY_DIR + File.separator + fileName),
          StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** 读取常量表字段 */
  private List<Map<String, String>> loadConstantFields(Workbook workbook, String beanClassName) {
    List<Map<String, String>> constantFields = new ArrayList<>();
    if (workbook == null) {
      return constantFields;
    }
    String constantSheetName = SystemConfigHolder.getInstance().getExcelConf().getDataConstantSheetName();
    int sheetIndex = workbook.getSheetIndex(constantSheetName);
    if (sheetIndex < 0) {
      return constantFields;
    }
    Sheet sheet = workbook.getSheetAt(sheetIndex);
    int typeCol = SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldTypeCol();
    int nameCol = SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldNameCol();
    int descCol = SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldDescCol();
    for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
      Row row = sheet.getRow(rowIndex);
      if (row == null) {
        continue;
      }
      Cell nameCell = row.getCell(nameCol);
      Cell typeCell = row.getCell(typeCol);
      Cell descCell = row.getCell(descCol);
      if (nameCell == null || typeCell == null) {
        continue;
      }
      String rawType = ExcelUtils.getCellValue(typeCell).trim();
      String name = ExcelUtils.getCellValue(nameCell).trim();
      String desc = descCell == null ? "" : ExcelUtils.getCellValue(descCell).trim();
      if (name.isEmpty() || rawType.isEmpty()) {
        continue;
      }
      ExcelFieldParseAdapter adapter = ExcelFieldParseAdapter.getFieldAdapterByTypeStr(rawType);
      if (adapter.getFieldAdapter() instanceof ExcelFieldParseAdapter.EnumFieldAdapter) {
        throw new ExcelParseException("常量配置不支持枚举配置");
      }
      String javaType = adapter.getFieldAdapter().getTargetFieldTypeStr(rawType);
      Map<String, String> field = new LinkedHashMap<>();
      field.put("name", name);
      field.put("desc", desc);
      field.put("type", toCppType(javaType, false, beanClassName));
      field.put("qualifiedType", toCppType(javaType, true, beanClassName));
      constantFields.add(field);
    }
    return constantFields;
  }

  private static boolean isEnum(String rawType) {
    return rawType != null && rawType.trim().matches("^\\w+\\(.*\\)$");
  }

  private static String enumNameOf(String rawType) {
    return ((ExcelFieldParseAdapter.EnumFieldAdapter)
            ExcelFieldParseAdapter.getFieldAdapterByTypeStr(rawType).getFieldAdapter())
        .getEnumClassName(rawType);
  }

  private static Set<String> enumValuesOf(String rawType) {
    return ((ExcelFieldParseAdapter.EnumFieldAdapter)
            ExcelFieldParseAdapter.getFieldAdapterByTypeStr(rawType).getFieldAdapter())
        .getEnumFieldSet(rawType);
  }

  private static String toJavaType(String rawType) {
    return ExcelFieldParseAdapter.getFieldAdapterByTypeStr(rawType)
        .getFieldAdapter()
        .getTargetFieldTypeStr(rawType);
  }

  /** Java类型 -> C++类型(枚举可按需加bean前缀) */
  private static String toCppType(String javaType, boolean qualified, String beanClassName) {
    if (javaType == null) {
      return "std::string";
    }
    String type = javaType.trim();
    switch (type) {
      case "byte":
      case "Byte":
        return "int8_t";
      case "short":
      case "Short":
        return "int16_t";
      case "int":
      case "Integer":
        return "int32_t";
      case "long":
      case "Long":
        return "int64_t";
      case "float":
      case "Float":
        return "float";
      case "double":
      case "Double":
        return "double";
      case "boolean":
      case "Boolean":
        return "bool";
      case "String":
        return "std::string";
      case "Date":
        return "cfg::CfgDateTime";
      default:
        break;
    }
    if (type.startsWith("List<") || type.startsWith("Set<")) {
      String sub = type.substring(type.indexOf('<') + 1, type.length() - 1);
      return "std::vector<" + toCppType(sub, qualified, beanClassName) + ">";
    }
    if (type.startsWith("Map<")) {
      String sub = type.substring(type.indexOf('<') + 1, type.length() - 1);
      int splitIndex = findTopLevelComma(sub);
      String keyType = sub.substring(0, splitIndex);
      String valueType = sub.substring(splitIndex + 1);
      return "std::unordered_map<" + toCppType(keyType, qualified, beanClassName) + ", "
          + toCppType(valueType, qualified, beanClassName) + ">";
    }
    // 枚举
    return qualified ? beanClassName + "::" + type : type;
  }

  private static int findTopLevelComma(String value) {
    int depth = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth--;
      } else if (c == ',' && depth == 0) {
        return i;
      }
    }
    return -1;
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
      className = className + StrUtils.upperFirst(suffix);
    }
    return StrUtils.upperFirst(className);
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
    private final String memberName;
    private final String accessorName;

    public ContainerDef(String className, String memberName, String accessorName) {
      this.className = className;
      this.memberName = memberName;
      this.accessorName = accessorName;
    }

    public String getClassName() {
      return className;
    }

    public String getMemberName() {
      return memberName;
    }

    public String getAccessorName() {
      return accessorName;
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

  public static CppTemplateGenerator getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final CppTemplateGenerator instance;

    Singleton() {
      this.instance = new CppTemplateGenerator();
    }

    public CppTemplateGenerator getInstance() {
      return instance;
    }
  }
}