package com.eouna.configtool.generator.template.csharp;

import com.eouna.configtool.configholder.ConfigDataBean.JavaTemplateConf;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.generator.base.ExcelFileStructure;
import com.eouna.configtool.generator.bean.ExcelDataStruct;
import com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelConstantFieldInfo;
import com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelEnumFieldInfo;
import com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelFieldInfo;
import com.eouna.configtool.generator.bean.ExcelSheetBean;
import com.eouna.configtool.generator.exceptions.ExcelParseException;
import com.eouna.configtool.generator.template.AbstractTemplateGenerator;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.generator.template.ExcelFieldParseAdapter;
import com.eouna.configtool.utils.ExcelUtils;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.StrUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 * C#模板生成器。
 *
 * <p>C# 有反射, 因此容器按"字段名 + excel类型串"在运行时反射赋值, 与Java模板思路一致。
 *
 * @author CCL
 */
public class CSharpTemplateGenerator extends AbstractTemplateGenerator {

  /** 程序集/项目名 */
  private static final String ASSEMBLY_NAME = "ConfigToolCfg";
  /** 源码目录 */
  private static final String SRC_DIR = "src";
  /** bean目录 */
  private static final String BEAN_DIR = "bean";
  /** container目录 */
  private static final String CONTAINER_DIR = "container";

  /** 枚举(枚举名 -> 值) */
  private final Map<String, Set<String>> enumCache = new ConcurrentSkipListMap<>();
  /** 容器定义 */
  private final Map<String, ContainerDef> containerDefMap = new ConcurrentSkipListMap<>();
  /** 已生成的bean */
  private final Set<String> generatedBeanSet = ConcurrentHashMap.newKeySet();

  @Override
  protected String getTemplateBindRelatedPath() {
    return ETemplateGenerator.CSHARP_GENERATOR.getTemplateHandler().getTemplateBindRelatedPath();
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
      String targetDir = getTargetDir();
      FileUtils.getOrCreateDir(targetDir + SRC_DIR);
      FileUtils.getOrCreateDir(targetDir + SRC_DIR + File.separator + BEAN_DIR);
      FileUtils.getOrCreateDir(targetDir + SRC_DIR + File.separator + CONTAINER_DIR);

      Map<String, Object> baseDataMap = new HashMap<>(8);
      baseDataMap.put("fieldInfo", new ExcelFieldInfo());
      baseDataMap.put("constFieldInfo", new ExcelConstantFieldInfo());
      baseDataMap.put(
          "constantSheetName",
          SystemConfigHolder.getInstance().getExcelConf().getDataConstantSheetName());
      baseDataMap.put("dataStartRow", ExcelTemplateGenUtils.getConfigFieldMaxRow() + 1);
      baseDataMap.put("idName", getJavaTemplateConf().getBaseBeanIdName());
      baseDataMap.put("skipStr", getJavaTemplateConf().getDataRangeServerSkipStr());
      generateTemplate(
          baseDataMap, "BaseCfgContainer.ftl", targetDir + SRC_DIR + File.separator + CONTAINER_DIR + File.separator + "BaseCfgContainer.cs");

      Map<String, Object> csprojDataMap = new HashMap<>(2);
      csprojDataMap.put("assemblyName", ASSEMBLY_NAME);
      generateTemplate(csprojDataMap, "Csproj.ftl", targetDir + ASSEMBLY_NAME + ".csproj");
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成C#基础运行库异常", e);
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
      throw new ExcelParseException("生成C# Bean失败, 存在重复的工作薄名: " + sheet.getSheetName());
    }
    ExcelDataStruct dataStruct = loadDataStruct(file, sheet);
    convertToCSharpTypes(dataStruct);
    // 枚举放入各自的cfg类(nested enum), 因此按表收集
    List<EnumDef> enumDefs = buildEnumDefs(dataStruct);

    Map<String, Object> beanDataMap = new HashMap<>(8);
    beanDataMap.put("beanClassName", beanClassName);
    beanDataMap.put("dataStruct", dataStruct);
    beanDataMap.put("date", getGenerateDate());
    beanDataMap.put("idName", getJavaTemplateConf().getBaseBeanIdName());
    beanDataMap.put("enumDefs", enumDefs);
    generateTemplate(
        beanDataMap, "CfgBean.ftl", targetDir() + SRC_DIR + File.separator + BEAN_DIR + File.separator + beanClassName + ".cs");

    List<ExcelConstantFieldInfo> constantFields = loadConstantFields(workbook);
    String containerClassName = beanClassName + "Container";
    Map<String, Object> containerDataMap = new HashMap<>(16);
    containerDataMap.put("containerClassName", containerClassName);
    containerDataMap.put("beanClassName", beanClassName);
    containerDataMap.put("bindExcelList", List.of(file.getName()));
    containerDataMap.put("sheetBean", sheetBean);
    containerDataMap.put("excelName", file.getName());
    containerDataMap.put("date", getGenerateDate());
    containerDataMap.put("constantFields", constantFields);
    generateTemplate(
        containerDataMap,
        "CfgContainer.ftl",
        targetDir() + SRC_DIR + File.separator + CONTAINER_DIR + File.separator + containerClassName + ".cs");
    containerDefMap.put(containerClassName, new ContainerDef(containerClassName, containerClassName));
  }

  @Override
  public void generatorAfter(
      TextAreaLogger textAreaLogger, Map<File, ExcelFileStructure> excelFileStructureMap) {
    try {
      JavaTemplateConf templateConf = getJavaTemplateConf();
      String targetDir = getTargetDir();
      List<ContainerDef> containerDefs = new ArrayList<>(containerDefMap.values());


      Map<String, Object> managerDataMap = new HashMap<>(8);
      managerDataMap.put("containerDefs", containerDefs);
      managerDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      managerDataMap.put("loadMethodName", StrUtils.upperFirst(templateConf.getDataManagerLoadDataCaller()));
      managerDataMap.put("date", getGenerateDate());
      generateTemplate(
          managerDataMap,
          "GameDataManager.ftl",
          targetDir + SRC_DIR + File.separator + templateConf.getDataManagerClassName() + ".cs");

      Map<String, Object> debugDataMap = new HashMap<>(8);
      debugDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      debugDataMap.put("loadMethodName", StrUtils.upperFirst(templateConf.getDataManagerLoadDataCaller()));
      debugDataMap.put(
          "excelLoadDir",
          SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath());
      debugDataMap.put("containerDefs", containerDefs);
      debugDataMap.put("date", getGenerateDate());
      generateTemplate(debugDataMap, "DebugMain.ftl", targetDir + SRC_DIR + File.separator + "Program.cs");

      if (textAreaLogger != null) {
        textAreaLogger.info("生成C#模板文件结束");
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成C#管理器/枚举时发生异常", e);
    } finally {
      generatedBeanSet.clear();
    }
  }

  private ExcelDataStruct loadDataStruct(File file, Sheet sheet) {
    ExcelDataStruct dataStruct = new ExcelDataStruct(file.getName(), sheet.getSheetName());
    String idName = getJavaTemplateConf().getBaseBeanIdName();
    Set<Integer> skipColList =
        ExcelTemplateGenUtils.getSkipCellList(sheet, getJavaTemplateConf().getDataRangeServerSkipStr());
    Set<ExcelFieldInfo> excelFields = ExcelTemplateGenUtils.getExcelFields(file, sheet, skipColList);
    if (excelFields == null) {
      return dataStruct;
    }
    Set<ExcelFieldInfo> fields = new LinkedHashSet<>(excelFields);
    fields.removeIf(fieldInfo -> idName.equalsIgnoreCase(fieldInfo.getFieldName().getFieldData()));
    dataStruct.getExcelFieldInfoList().addAll(fields);
    return dataStruct;
  }

  private List<ExcelConstantFieldInfo> loadConstantFields(Workbook workbook) {
    List<ExcelConstantFieldInfo> constantFields = new ArrayList<>();
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
      String typeValue = ExcelUtils.getCellValue(typeCell).trim();
      String nameValue = ExcelUtils.getCellValue(nameCell).trim();
      String descValue = descCell == null ? "" : ExcelUtils.getCellValue(descCell).trim();
      if (nameValue.isEmpty() || typeValue.isEmpty()) {
        continue;
      }
      ExcelFieldParseAdapter adapter = ExcelFieldParseAdapter.getFieldAdapterByTypeStr(typeValue);
      if (adapter.getFieldAdapter() instanceof ExcelFieldParseAdapter.EnumFieldAdapter) {
        throw new ExcelParseException("常量配置不支持枚举配置");
      }
      String targetType = adapter.getFieldAdapter().getTargetFieldTypeStr(typeValue);
      ExcelConstantFieldInfo constantField = new ExcelConstantFieldInfo();
      constantField.getFieldName().setFieldData(nameValue);
      constantField.getFieldDesc().setFieldData(descValue);
      constantField.getFieldType().setFieldData(toCSharpType(targetType));
      constantFields.add(constantField);
    }
    return constantFields;
  }

  /** 收集本表使用的枚举(枚举会作为nested enum生成到该表的cfg类中) */
  private static List<EnumDef> buildEnumDefs(ExcelDataStruct dataStruct) {
    Map<String, Set<String>> enums = new TreeMap<>();
    for (ExcelFieldInfo fieldInfo : dataStruct.getExcelFieldInfoList()) {
      if (fieldInfo instanceof ExcelEnumFieldInfo enumFieldInfo) {
        enums
            .computeIfAbsent(enumFieldInfo.getEnumClassName(), key -> new TreeSet<>())
            .addAll(enumFieldInfo.getEnumFieldData());
      }
    }
    List<EnumDef> enumDefs = new ArrayList<>();
    for (Map.Entry<String, Set<String>> entry : enums.entrySet()) {
      enumDefs.add(new EnumDef(entry.getKey(), new ArrayList<>(entry.getValue())));
    }
    return enumDefs;
  }

  private static String toCSharpType(String javaType) {
    if (javaType == null) {
      return "string";
    }
    String type = javaType.trim();
    switch (type) {
      case "byte":
      case "Byte":
        return "sbyte";
      case "short":
      case "Short":
        return "short";
      case "int":
      case "Integer":
        return "int";
      case "long":
      case "Long":
        return "long";
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
        return "string";
      case "Date":
        return "DateTime";
      default:
        break;
    }
    if (type.startsWith("List<") || type.startsWith("Set<")) {
      String sub = type.substring(type.indexOf('<') + 1, type.length() - 1);
      return "List<" + toCSharpType(sub) + ">";
    }
    if (type.startsWith("Map<")) {
      String sub = type.substring(type.indexOf('<') + 1, type.length() - 1);
      int splitIndex = findTopLevelComma(sub);
      String keyType = sub.substring(0, splitIndex);
      String valueType = sub.substring(splitIndex + 1);
      return "Dictionary<" + toCSharpType(keyType) + ", " + toCSharpType(valueType) + ">";
    }
    // 枚举
    return type;
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

  private static void convertToCSharpTypes(ExcelDataStruct dataStruct) {
    for (ExcelFieldInfo fieldInfo : dataStruct.getExcelFieldInfoList()) {
      fieldInfo.getFieldType().setFieldData(toCSharpType(fieldInfo.getFieldType().getFieldData()));
    }
  }

  private String getCfgBeanClassName(String sheetName) {
    String suffix = "cfg";
    String className = sheetName;
    if (!className.endsWith(StrUtils.upperFirst(suffix))) {
      className =
          className.endsWith(suffix)
              ? className.substring(className.lastIndexOf(suffix)) + StrUtils.upperFirst(suffix)
              : className + StrUtils.upperFirst(suffix);
    }
    return StrUtils.upperFirst(className);
  }

  private String getGenerateDate() {
    return new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss").format(new Date());
  }

  private static String getTargetDir() {
    return SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
        + File.separator + "csharp" + File.separator;
  }

  private static String targetDir() {
    return getTargetDir();
  }

  private static JavaTemplateConf getJavaTemplateConf() {
    return SystemConfigHolder.getInstance().getJavaTemplateConf();
  }

  /** 容器定义 */
  public static class ContainerDef {
    private final String className;
    private final String fieldName;

    public ContainerDef(String className, String fieldName) {
      this.className = className;
      this.fieldName = fieldName;
    }

    public String getClassName() {
      return className;
    }

    public String getFieldName() {
      return fieldName;
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

  public static CSharpTemplateGenerator getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final CSharpTemplateGenerator instance;

    Singleton() {
      this.instance = new CSharpTemplateGenerator();
    }

    public CSharpTemplateGenerator getInstance() {
      return instance;
    }
  }
}
