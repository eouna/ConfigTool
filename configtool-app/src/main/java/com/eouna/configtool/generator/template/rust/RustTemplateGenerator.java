package com.eouna.configtool.generator.template.rust;

import com.eouna.configtool.configholder.ConfigDataBean.ExcelGenPathConf;
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
import freemarker.template.TemplateException;
import java.io.File;
import java.io.IOException;
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
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Rust模板生成器。
 *
 * <p>Rust 没有反射, 因此容器按"每个字段生成一行类型化解析代码"的方式生成; 枚举集中声明在 bean/enums.rs。
 *
 * @author CCL
 */
public class RustTemplateGenerator extends AbstractTemplateGenerator {

  /** Rust crate 名 */
  private static final String RUST_CRATE = "config_tool_cfg";
  /** 源码根目录 */
  private static final String SRC_DIR = "src";

  /** 所有枚举(枚举名 -> 值), 并行生成需线程安全 */
  private final Map<String, Set<String>> enumCache = new ConcurrentSkipListMap<>();
  /** 容器定义(容器类名 -> 定义) */
  private final Map<String, ContainerDef> containerDefMap = new ConcurrentSkipListMap<>();
  /** bean类名 -> 模块名 */
  private final Map<String, String> beanModuleMap = new ConcurrentSkipListMap<>();
  /** 已生成的bean类名(用于重复sheet校验) */
  private final Set<String> generatedBeanSet = ConcurrentHashMap.newKeySet();

  @Override
  protected String getTemplateBindRelatedPath() {
    return ETemplateGenerator.RUST_GENERATOR.getTemplateHandler().getTemplateBindRelatedPath();
  }

  @Override
  public void generatorBefore(
      List<File> successGenList,
      Map<File, ExcelFileStructure> excelFileStructureMap,
      List<Exception> exceptionCollector) {
    try {
      enumCache.clear();
      containerDefMap.clear();
      beanModuleMap.clear();
      generatedBeanSet.clear();
      // 生成基础运行库与Cargo.toml
      String targetDir = getTargetDir();
      FileUtils.getOrCreateDir(targetDir + SRC_DIR);
      FileUtils.getOrCreateDir(targetDir + SRC_DIR + File.separator + "bean");
      FileUtils.getOrCreateDir(targetDir + SRC_DIR + File.separator + "container");

      Map<String, Object> containerDataMap = new HashMap<>(8);
      containerDataMap.put("fieldInfo", new ExcelFieldInfo());
      containerDataMap.put("constFieldInfo", new ExcelConstantFieldInfo());
      containerDataMap.put(
          "constantSheetName", SystemConfigHolder.getInstance().getExcelConf().getDataConstantSheetName());
      containerDataMap.put("dataStartRow", ExcelTemplateGenUtils.getConfigFieldMaxRow() + 1);
      containerDataMap.put("idName", getJavaTemplateConf().getBaseBeanIdName());
      containerDataMap.put(
          "skipStr", getJavaTemplateConf().getDataRangeServerSkipStr());
      generateTemplate(
          containerDataMap,
          "BaseCfgContainer.ftl",
          targetDir + SRC_DIR + File.separator + "container" + File.separator + "base_cfg_container.rs");

      Map<String, Object> cargoDataMap = new HashMap<>(2);
      cargoDataMap.put("crateName", RUST_CRATE);
      generateTemplate(cargoDataMap, "CargoToml.ftl", targetDir + "Cargo.toml");
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成Rust基础运行库异常", e);
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
      // Rust不生成虚拟父表(无继承), 直接跳过
      return;
    }
    String beanClassName = getCfgBeanClassName(sheet.getSheetName());
    if (!generatedBeanSet.add(beanClassName)) {
      throw new ExcelParseException(
          "生成Rust Bean失败, 存在重复的工作薄名: " + sheet.getSheetName());
    }
    // 字段信息
    ExcelDataStruct dataStruct = loadDataStruct(file, sheet);
    convertToRustTypes(dataStruct);
    // 枚举跟随各自的cfg(同文件内嵌), 因此按表收集
    List<EnumDef> enumDefs = buildEnumDefs(dataStruct);
    beanModuleMap.put(beanClassName, toSnakeCase(beanClassName));

    writeBean(beanClassName, dataStruct, enumDefs);

    List<ExcelConstantFieldInfo> constantFields = loadConstantFields(workbook);
    String containerClassName = beanClassName + "Container";
    writeContainer(
        file, sheet, sheetBean, beanClassName, containerClassName, dataStruct, constantFields);
    successGenListPlaceholder();
  }

  private void successGenListPlaceholder() {
    // 生成结果的记录由外部统一处理(ExcelTemplateGenUtils), 此处无需处理
  }

  /** 读取excel字段信息(去掉id列, id由每个bean的id字段承载) */
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
    fields.removeIf(
        fieldInfo ->
            idName.equalsIgnoreCase(fieldInfo.getFieldName().getFieldData()));
    dataStruct.getExcelFieldInfoList().addAll(fields);
    return dataStruct;
  }

  /** 读取常量字段 */
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
      constantField.getFieldType().setFieldData(toRustType(targetType));
      constantFields.add(constantField);
    }
    return constantFields;
  }

  /** 生成bean文件 */
  private void writeBean(String beanClassName, ExcelDataStruct dataStruct, List<EnumDef> enumDefs)
      throws IOException, TemplateException {
    Map<String, Object> dataMap = new HashMap<>(8);
    dataMap.put("beanClassName", beanClassName);
    dataMap.put("beanModule", toSnakeCase(beanClassName));
    dataMap.put("dataStruct", dataStruct);
    dataMap.put("date", getGenerateDate());
    dataMap.put("idName", getJavaTemplateConf().getBaseBeanIdName());
    dataMap.put("enumDefs", enumDefs);
    generateTemplate(
        dataMap,
        "CfgBean.ftl",
        getTargetDir() + SRC_DIR + File.separator + "bean" + File.separator + toSnakeCase(beanClassName) + ".rs");
  }

  /** 生成容器文件 */
  private void writeContainer(
      File file,
      Sheet sheet,
      ExcelSheetBean sheetBean,
      String beanClassName,
      String containerClassName,
      ExcelDataStruct dataStruct,
      List<ExcelConstantFieldInfo> constantFields)
      throws IOException, TemplateException {
    Map<String, Object> dataMap = new HashMap<>(16);
    dataMap.put("containerClassName", containerClassName);
    dataMap.put("containerModule", toSnakeCase(containerClassName));
    dataMap.put("beanClassName", beanClassName);
    dataMap.put("beanModule", toSnakeCase(beanClassName));
    dataMap.put("bindExcelList", List.of(file.getName()));
    dataMap.put("sheetBean", sheetBean);
    dataMap.put("excelName", file.getName());
    dataMap.put("date", getGenerateDate());
    dataMap.put("idName", getJavaTemplateConf().getBaseBeanIdName());
    dataMap.put("dataStartRow", ExcelTemplateGenUtils.getConfigFieldMaxRow() + 1);
    dataMap.put("constantSheetName", SystemConfigHolder.getInstance().getExcelConf().getDataConstantSheetName());
    dataMap.put("fieldInfo", new ExcelFieldInfo());
    dataMap.put("constFieldInfo", new ExcelConstantFieldInfo());
    dataMap.put("dataStruct", dataStruct);
    dataMap.put("constantFields", constantFields);
    generateTemplate(
        dataMap,
        "CfgContainer.ftl",
        getTargetDir() + SRC_DIR + File.separator + "container" + File.separator + toSnakeCase(containerClassName) + ".rs");
    containerDefMap.put(
        containerClassName,
        new ContainerDef(
            containerClassName, toSnakeCase(containerClassName), toSnakeCase(containerClassName)));
  }

  @Override
  public void generatorAfter(
      TextAreaLogger textAreaLogger, Map<File, ExcelFileStructure> excelFileStructureMap) {
    try {
      JavaTemplateConf templateConf = getJavaTemplateConf();
      String targetDir = getTargetDir();
      List<ContainerDef> containerDefs = new ArrayList<>(containerDefMap.values());


      // bean/mod.rs
      Map<String, Object> beanModDataMap = new HashMap<>(4);
      beanModDataMap.put("beanModules", new ArrayList<>(new TreeSet<>(beanModuleMap.values())));
      generateTemplate(
          beanModDataMap, "BeanMod.ftl", targetDir + SRC_DIR + File.separator + "bean" + File.separator + "mod.rs");

      // container/mod.rs
      Map<String, Object> containerModDataMap = new HashMap<>(4);
      containerModDataMap.put(
          "containerModules",
          containerDefs.stream().map(ContainerDef::getModuleName).sorted().collect(Collectors.toList()));
      generateTemplate(
          containerModDataMap,
          "ContainerMod.ftl",
          targetDir + SRC_DIR + File.separator + "container" + File.separator + "mod.rs");

      // 数据管理器
      Map<String, Object> managerDataMap = new HashMap<>(8);
      managerDataMap.put("containerDefs", containerDefs);
      managerDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      managerDataMap.put("loadMethodName", toSnakeCase(templateConf.getDataManagerLoadDataCaller()));
      managerDataMap.put("date", getGenerateDate());
      generateTemplate(
          managerDataMap, "GameDataManager.ftl", targetDir + SRC_DIR + File.separator + "game_data_manager.rs");

      // lib.rs
      Map<String, Object> libDataMap = new HashMap<>(2);
      libDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      generateTemplate(libDataMap, "Lib.ftl", targetDir + SRC_DIR + File.separator + "lib.rs");

      // 临时调试入口
      Map<String, Object> debugDataMap = new HashMap<>(8);
      debugDataMap.put("crateName", RUST_CRATE);
      debugDataMap.put("dataManagerClassName", templateConf.getDataManagerClassName());
      debugDataMap.put("loadMethodName", toSnakeCase(templateConf.getDataManagerLoadDataCaller()));
      debugDataMap.put(
          "excelLoadDir",
          SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath().replace("\\", "\\\\"));
      debugDataMap.put("containerDefs", containerDefs);
      debugDataMap.put("date", getGenerateDate());
      generateTemplate(debugDataMap, "DebugMain.ftl", targetDir + SRC_DIR + File.separator + "main.rs");

      if (textAreaLogger != null) {
        textAreaLogger.info("生成Rust模板文件结束");
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成Rust管理器/枚举时发生异常", e);
    } finally {
      generatedBeanSet.clear();
    }
  }

  /** 收集本表使用的枚举(枚举与其cfg同文件内嵌) */
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

  /** Java类型 -> Rust类型 */
  private static String toRustType(String javaType) {
    if (javaType == null) {
      return "String";
    }
    String type = javaType.trim();
    switch (type) {
      case "byte":
      case "Byte":
        return "i8";
      case "short":
      case "Short":
        return "i16";
      case "int":
      case "Integer":
        return "i32";
      case "long":
      case "Long":
        return "i64";
      case "float":
      case "Float":
        return "f32";
      case "double":
      case "Double":
        return "f64";
      case "boolean":
      case "Boolean":
        return "bool";
      case "String":
        return "String";
      case "Date":
        return "crate::container::CfgDateTime";
      default:
        break;
    }
    if (type.startsWith("List<") || type.startsWith("Set<")) {
      String sub = type.substring(type.indexOf('<') + 1, type.length() - 1);
      return "Vec<" + toRustType(sub) + ">";
    }
    if (type.startsWith("Map<")) {
      String sub = type.substring(type.indexOf('<') + 1, type.length() - 1);
      int splitIndex = findTopLevelComma(sub);
      String keyType = sub.substring(0, splitIndex);
      String valueType = sub.substring(splitIndex + 1);
      return "std::collections::HashMap<" + toRustType(keyType) + ", " + toRustType(valueType) + ">";
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

  /** 将数据结构中的Java类型转换为Rust类型 */
  private static void convertToRustTypes(ExcelDataStruct dataStruct) {
    for (ExcelFieldInfo fieldInfo : dataStruct.getExcelFieldInfoList()) {
      fieldInfo.getFieldType().setFieldData(toRustType(fieldInfo.getFieldType().getFieldData()));
    }
  }

  /** 大驼峰转下划线 */
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

  private String getCfgBeanClassName(String sheetName) {
    String cfgBeanClassSuffix = "cfg";
    String cfgBeanClassName = sheetName;
    if (!cfgBeanClassName.endsWith(StrUtils.upperFirst(cfgBeanClassSuffix))) {
      cfgBeanClassName =
          cfgBeanClassName.endsWith(cfgBeanClassSuffix)
              ? cfgBeanClassName.substring(cfgBeanClassName.lastIndexOf(cfgBeanClassSuffix))
                  + StrUtils.upperFirst(cfgBeanClassSuffix)
              : cfgBeanClassName + StrUtils.upperFirst(cfgBeanClassSuffix);
    }
    return StrUtils.upperFirst(cfgBeanClassName);
  }

  private String getGenerateDate() {
    return new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss").format(new Date());
  }

  private static String getTargetDir() {
    return SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
        + File.separator + "rust" + File.separator;
  }

  private static JavaTemplateConf getJavaTemplateConf() {
    return SystemConfigHolder.getInstance().getJavaTemplateConf();
  }

  /** 容器定义 */
  public static class ContainerDef {
    private final String className;
    private final String moduleName;
    private final String fieldName;

    public ContainerDef(String className, String moduleName, String fieldName) {
      this.className = className;
      this.moduleName = moduleName;
      this.fieldName = fieldName;
    }

    public String getClassName() {
      return className;
    }

    public String getModuleName() {
      return moduleName;
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

  public static RustTemplateGenerator getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final RustTemplateGenerator instance;

    Singleton() {
      this.instance = new RustTemplateGenerator();
    }

    public RustTemplateGenerator getInstance() {
      return instance;
    }
  }
}