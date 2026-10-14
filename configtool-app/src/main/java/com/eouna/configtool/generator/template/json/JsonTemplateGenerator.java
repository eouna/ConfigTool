package com.eouna.configtool.generator.template.json;

import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.generator.base.ExcelFileStructure;
import com.eouna.configtool.generator.bean.ExcelDataStruct;
import com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelFieldInfo;
import com.eouna.configtool.generator.bean.ExcelSheetBean;
import com.eouna.configtool.generator.exceptions.ExcelFormatCheckException;
import com.eouna.configtool.generator.exceptions.ExcelParseException;
import com.eouna.configtool.generator.template.AbstractTemplateGenerator;
import com.eouna.configtool.generator.template.AbstractTemplateHandler;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.generator.template.ExcelFieldParseAdapter;
import com.eouna.configtool.utils.ExcelUtils;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.utils.MemUsageUtils;
import com.eouna.configtool.utils.StrUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;

/**
 * json模板生成器
 *
 * @author CCL
 * @date 2023/3/10
 */
public class JsonTemplateGenerator extends AbstractTemplateGenerator {

  /** json数据持有者 */
  private final Map<String, HashMap<Integer, Object>> jsonDataKeeper =
      new ConcurrentSkipListMap<>();

  /** json内容: 常量段字段名 */
  private static final String JSON_FIELD_CONST = "const";

  /** json内容: 数据段字段名 */
  private static final String JSON_FIELD_DATA = "data";

  /**
   * 常量表数据(数据表名 -> 常量行, 首行为excel常量表的表头)。
   *
   * <p>常量表在excel中单独维护, json导出时按excel同样的行列布局放进该表json的 {@code const} 段,
   * 各语言json数据源据此还原常量。
   */
  private final Map<String, List<List<String>>> constantSheetRowsBySheet =
      new ConcurrentSkipListMap<>();

  /**
   * 单例
   *
   * @return JsonTemplateGenerator
   */
  public static JsonTemplateGenerator getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  @Override
  public void generatorBefore(
      List<File> successGenList,
      Map<File, ExcelFileStructure> excelFileStructureMap,
      List<Exception> exceptions) {
    jsonDataKeeper.clear();
    resetConstantSheetRows();
    Set<String> generatedParentList = new HashSet<>();
    try {
      for (Map.Entry<File, ExcelFileStructure> fileSture : excelFileStructureMap.entrySet()) {
        File currentDealFile = fileSture.getKey();
        if (generatedParentList.contains(currentDealFile.getName())) {
          continue;
        }
        ExcelFileStructure fileStureValue = fileSture.getValue();
        // 过滤父类
        if (currentDealFile != fileStureValue.getParentFile()) {
          String parentName =
              currentDealFile.getName()
                  .split(DefaultEnvConfigConstant.EXCEL_STRUCTURE_DELIMITER)[0];
          // 加载父类数据
          loadSheetData(
              currentDealFile, new ExcelSheetBean(currentDealFile, parentName), fileStureValue);
          // 生成成功后的文件
          generatedParentList.add(currentDealFile.getName());
        }
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成父Json模板时发生异常", e);
      exceptions.add(e);
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
    // 检查是否有重复的工作薄名
    checkRepeatSheetName(sheet);
    // 开始解析单个文件
    loadSheetData(file, sheetBean, excelFileStructure);
    // 收集常量表(该表json的const段)
    collectConstantSheetRows(workbook, sheet.getSheetName());
  }

  /** 重置常量表收集结果(每次生成前调用) */
  private synchronized void resetConstantSheetRows() {
    constantSheetRowsBySheet.clear();
  }

  /**
   * 收集常量表数据。
   *
   * <p>常量表(excel中的 {@code dataConstantSheetName} 工作薄)按excel的行列布局收集: 第0行为表头(容器解析时
   * 会跳过), 其余行为常量行; 同名常量以最后出现的行为准(与excel逐行赋值的行为一致)。
   *
   * @param workbook 当前excel工作薄
   */
  private synchronized void collectConstantSheetRows(Workbook workbook, String sheetName) {
    if (workbook == null || StringUtils.isEmpty(sheetName)) {
      return;
    }
    String constantSheetName =
        SystemConfigHolder.getInstance().getExcelConf().getDataConstantSheetName();
    if (StringUtils.isEmpty(constantSheetName)) {
      return;
    }
    Sheet constantSheet = workbook.getSheet(constantSheetName);
    if (constantSheet == null) {
      return;
    }
    int nameCol =
        SystemConfigHolder.getInstance().getExcelConf().getConstantFieldRow().getFieldNameCol();
    DataFormatter dataFormatter = new DataFormatter();
    FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
    List<String> titleRow = null;
    Map<String, List<String>> rowsByName = new LinkedHashMap<>();
    for (int rowIndex = 0; rowIndex <= constantSheet.getLastRowNum(); rowIndex++) {
      Row row = constantSheet.getRow(rowIndex);
      if (ExcelUtils.isBlankRow(row)) {
        continue;
      }
      List<String> cellTexts = readRowCellTexts(row, dataFormatter, evaluator);
      // 第0行固定是表头, 与excel保持一致(容器解析常量时会跳过该行)
      if (rowIndex == 0) {
        if (titleRow == null) {
          titleRow = cellTexts;
        }
        continue;
      }
      String fieldName = nameCol < cellTexts.size() ? cellTexts.get(nameCol).trim() : "";
      if (fieldName.isEmpty()) {
        continue;
      }
      rowsByName.put(fieldName, cellTexts);
    }
    if (titleRow == null && rowsByName.isEmpty()) {
      return;
    }
    List<List<String>> exportRows = new ArrayList<>(rowsByName.size() + 1);
    exportRows.add(titleRow == null ? new ArrayList<>() : titleRow);
    exportRows.addAll(rowsByName.values());
    constantSheetRowsBySheet.put(sheetName, exportRows);
  }

  /** 取某张表的常量段(const): 按excel行列布局的字符串表, 没有常量时返回空数组 */
  private List<List<String>> getConstantSheetRows(String sheetName) {
    List<List<String>> rows;
    synchronized (this) {
      rows = constantSheetRowsBySheet.get(sheetName);
    }
    if (rows == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(rows);
  }

  /**
   * 组装单张表的json内容。
   *
   * <p>结构: {@code {"const": [[常量表行]], "data": {"<id>": {字段: 值}}}}。
   * {@code const} 为该表所在excel文件里常量表的内容(字符串表, 首行为表头), 没有常量时为空数组;
   * {@code data} 为excel里的配置数据(与旧版json内容一致)。
   *
   * @param sheetName 表名
   * @param sheetData 配置数据(id -> 行数据)
   * @return json内容
   */
  private Map<String, Object> buildSheetJsonData(String sheetName, Map<Integer, Object> sheetData) {
    Map<String, Object> sheetJsonData = new LinkedHashMap<>(4);
    sheetJsonData.put(JSON_FIELD_CONST, getConstantSheetRows(sheetName));
    sheetJsonData.put(JSON_FIELD_DATA, sheetData);
    return sheetJsonData;
  }

  /** 读取一整行的单元格文本(数字按excel显示格式, 避免出现多余的 .0) */
  private static List<String> readRowCellTexts(
      Row row, DataFormatter dataFormatter, FormulaEvaluator evaluator) {
    List<String> cellTexts = new ArrayList<>();
    int lastCellNum = row.getLastCellNum();
    for (int col = 0; col < lastCellNum; col++) {
      Cell cell = row.getCell(col);
      cellTexts.add(cell == null ? "" : dataFormatter.formatCellValue(cell, evaluator));
    }
    return cellTexts;
  }

  /**
   * 加载数据
   *
   * @param file 当前文件
   * @param sheet 当前工作薄
   * @param excelFileStructure 文件结构
   */
  public void loadSheetData(File file, ExcelSheetBean sheet, ExcelFileStructure excelFileStructure)
      throws IOException {
    // 是否是父类
    boolean isParent = file != excelFileStructure.getParentFile();
    // 获取子excel列表
    Collection<File> childExcelList =
        isParent ? excelFileStructure.getChildFileSet() : Collections.singletonList(file);
    int rowDataStartNum = ExcelTemplateGenUtils.getConfigFieldMaxRow() + 1;
    HashMap<Integer, Object> rowDataMap = new HashMap<>(8);
    // 如果有多个excel需要加载子类的数据
    for (File childExcel : childExcelList) {
      try (Workbook wb = WorkbookFactory.create(childExcel, null, true)) {
        Sheet curDealSheet = wb.getSheetAt(0);
        // 获取需要跳过的列
        Set<Integer> skipColList = getSkipCellList(curDealSheet);
        // 加载字段
        Map<Integer, ExcelFieldInfo> fieldInfoMap =
            ExcelTemplateGenUtils.loadColFieldInfoMap(curDealSheet, curDealSheet.getRow(0));
        Map<String, Set<String>> enumInfoMap = new HashMap<>(8);
        for (Entry<Integer, ExcelFieldInfo> entry : fieldInfoMap.entrySet()) {
          // 如果是枚举字段信息 添加枚举信息
          if (entry.getValue() instanceof ExcelDataStruct.ExcelEnumFieldInfo) {
            ExcelDataStruct.ExcelEnumFieldInfo enumFieldInfo =
                ((ExcelDataStruct.ExcelEnumFieldInfo) entry.getValue());
            String enumClassName = enumFieldInfo.getEnumClassName();
            enumInfoMap.put(StrUtils.upperFirst(enumClassName), enumFieldInfo.getEnumFieldData());
          }
        }
        if (!rowDataMap.isEmpty()) {
          // 添加枚举数据
          rowDataMap.put(0, enumInfoMap);
        }
        // 加载数据
        for (int i = rowDataStartNum; i <= curDealSheet.getLastRowNum(); i++) {
          Row dealRow = curDealSheet.getRow(i);
          // 空行跳过
          if (ExcelUtils.isBlankRow(dealRow)) {
            continue;
          }
          // 加载单行数据
          handleRowData(curDealSheet.getRow(i), fieldInfoMap, skipColList, rowDataMap, enumInfoMap);
        }
      }
    }
    // 兼容有父类的情况
    if (!jsonDataKeeper.containsKey(sheet.getSheetName())) {
      // 保存数据
      jsonDataKeeper.put(sheet.getSheetName(), rowDataMap);
    } else {
      jsonDataKeeper.get(sheet.getSheetName()).putAll(rowDataMap);
    }
  }

  /** 获取需要跳过的列 */
  private Set<Integer> getSkipCellList(Sheet sheet) {
    int dataFieldRangeRowNum =
        SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldDataRangeRow();
    Set<Integer> skipCellList = new HashSet<>();
    Row dataFieldRangeRow = sheet.getRow(dataFieldRangeRowNum);
    if (dataFieldRangeRow == null || dataFieldRangeRow.getLastCellNum() <= 0) {
      return skipCellList;
    }
    for (Cell cell : dataFieldRangeRow) {
      String cellValue = ExcelUtils.getCellValue(cell);
      String clientSkipStr =
          SystemConfigHolder.getInstance().getJavaTemplateConf().getDataRangeClientSkipStr();
      if (!StringUtils.isEmpty(cellValue) && clientSkipStr.equalsIgnoreCase(cellValue)) {
        skipCellList.add(cell.getColumnIndex());
      }
    }
    return skipCellList;
  }

  /**
   * 处理单行数据
   *
   * @param row row数据
   * @param excelFieldInfoMap 每列对应的行数据
   * @param skipCellList 需要跳过的列
   * @param rowDataMap 行数据容器
   */
  private void handleRowData(
      Row row,
      Map<Integer, ExcelFieldInfo> excelFieldInfoMap,
      Set<Integer> skipCellList,
      Map<Integer, Object> rowDataMap,
      Map<String, Set<String>> enumInfoMap) {
    int rowId = Integer.MIN_VALUE;
    Map<String, Object> colDataMap = new HashMap<>(8);
    // 遍历单行中每列的数据
    for (int i = 0; i <= row.getLastCellNum(); i++) {
      // 需要跳过
      if (skipCellList.contains(i)) {
        continue;
      }
      // 如果包含才进行加载 不包含的情况可能是有空列
      if (excelFieldInfoMap.containsKey(i)) {
        // 读取每列中的类型
        ExcelFieldInfo excelFieldInfo = excelFieldInfoMap.get(i);
        Cell cell = row.getCell(i);
        if (cell == null) {
          if (i == 0) {
            // ID列不能为空
            throw new ExcelParseException(
                row.getSheet().getSheetName(),
                "配置表数据解析异常,ID列不能为空, 行: " + row.getRowNum() + " 列: 0");
          }
          continue;
        }
        // 通过每列的类型解析每列中的数据
        String fieldType = excelFieldInfo.getFieldType().getFieldData();
        // 将解析出的数据赋值给cfgBean中相应的字段
        String fieldName = excelFieldInfo.getFieldName().getFieldData();
        Object cellData;
        try {
          // 解析excel中的数据
          cellData = parseCellData(fieldType, cell, enumInfoMap);
        } catch (Exception e) {
          LoggerUtils.getLogger().error("err", e);
          // 如果此处发生异常说明是配置表格式发生了错误
          throw new ExcelParseException(
              excelFieldInfo, row, cell, "配置表数据解析异常,请检查单元格数据格式, 异常信息: " + e.getMessage());
        }
        if (i == 0 && cellData == null) {
          // ID列不能为空
          throw new ExcelParseException(excelFieldInfo, row, cell, "配置表数据解析异常,ID列不能为空");
        } else if (i == 0) {
          rowId = (int) cellData;
        }
        // 保存列数据
        colDataMap.put(fieldName, cellData);
      }
    }
    if (rowDataMap.containsKey(rowId)) {
      throw new ExcelParseException(row.getSheet().getSheetName(), "配置表ID重复: " + rowId);
    } else {
      // 保存行数据
      rowDataMap.put(rowId, colDataMap);
    }
  }

  /** 解析列数据 */
  protected Object parseCellData(
      String fieldType, Cell cell, Map<String, Set<String>> enumInfoMap) {
    ExcelFieldParseAdapter fieldDataAdapter =
        ExcelFieldParseAdapter.getFieldAdapterByTypeStr(fieldType);
    String cellString = cell.toString().trim();
    if (fieldDataAdapter.getFieldAdapter() instanceof ExcelFieldParseAdapter.EnumFieldAdapter) {
      String enumFieldType = StrUtils.upperFirst(fieldType);
      if (enumInfoMap.containsKey(enumFieldType)) {
        // 如果在枚举中未找到
        if (!enumInfoMap.get(enumFieldType).contains(cellString)) {
          throw new RuntimeException(
              "未在枚举: "
                  + String.join(",", enumInfoMap.get(enumFieldType))
                  + " 中找到字段: "
                  + cellString);
        }
      }
    }
    return fieldDataAdapter.getFieldAdapter().parseFiledStrToJavaClassType(cellString, fieldType);
  }

  /**
   * 检查重复的工作薄名
   *
   * @param sheet 工作薄名
   */
  private void checkRepeatSheetName(Sheet sheet) {
    if (jsonDataKeeper.containsKey(sheet.getSheetName())) {
      throw new ExcelFormatCheckException("拥有重复的工作薄名: " + sheet.getSheetName());
    }
  }

  @Override
  public void generatorAfter(
      TextAreaLogger textAreaLogger, Map<File, ExcelFileStructure> excelFileStructureMap) {
    // 是否分为多个json文件
    if (SystemConfigHolder.getInstance().getJsonTemplateConf().isSplitMultiJson()) {
      splitMultiJsonFile(textAreaLogger);
    } else {
      mergeSingleJsonFile(textAreaLogger);
    }
  }

  @Override
  protected String getTemplateBindRelatedPath() {
    return ETemplateGenerator.JSON_GENERATOR.getTemplateHandler().getTemplateBindRelatedPath();
  }

  /** 将excel数据合并为一个大的json文件 */
  private void mergeSingleJsonFile(TextAreaLogger textAreaLogger) {
    File outJsonFile =
        new File(
            SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
                + File.separator
                + "out.json");
    try {
      // 合并模式: 每个表名对应 {const, data}
      Map<String, Object> allSheetJsonData = new LinkedHashMap<>(jsonDataKeeper.size());
      for (Entry<String, HashMap<Integer, Object>> entry : jsonDataKeeper.entrySet()) {
        allSheetJsonData.put(
            entry.getKey(), buildSheetJsonData(entry.getKey(), entry.getValue()));
      }
      Gson gson = getGsonSerialize();
      String luaDataJsonStr = gson.toJson(allSheetJsonData);
      FileUtils.writeStringToFile(outJsonFile, luaDataJsonStr, StandardCharsets.UTF_8);
      textAreaLogger.info("生成json文件模板结束,文件大小: {}", MemUsageUtils.humanSizeOf(luaDataJsonStr));
    } catch (IOException ioException) {
      textAreaLogger.error("导出json文件错误");
    }
  }

  /** 将excel导出为多个不同的json文件 */
  private void splitMultiJsonFile(TextAreaLogger textAreaLogger) {
    try {
      for (Entry<String, HashMap<Integer, Object>> entry : jsonDataKeeper.entrySet()) {
        String sheetName = entry.getKey();
        String targetGenDir =
            SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir();
        AbstractTemplateHandler jsonTemplateHandler =
            ETemplateGenerator.JSON_GENERATOR.getTemplateHandler();
        File outJsonFile =
            new File(
                targetGenDir
                    + File.separator
                    + jsonTemplateHandler.getTemplateBindRelatedPath()
                    + File.separator
                    + sheetName
                    + jsonTemplateHandler.getFileIdentifier());
        Gson gson = getGsonSerialize();
        // 每张表: {const: [常量行], data: {id: {字段: 值}}}
        String jsonDataJsonStr = gson.toJson(buildSheetJsonData(sheetName, entry.getValue()));
        FileUtils.writeStringToFile(outJsonFile, jsonDataJsonStr, StandardCharsets.UTF_8);
      }
      textAreaLogger.info("生成json文件模板结束");
    } catch (IOException ioException) {
      textAreaLogger.error("导出json文件错误");
    } catch (Exception e) {
      LoggerUtils.getLogger().error("", e);
    }
  }

  private Gson getGsonSerialize() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(Date.class, new DateTypeAdapter());
    return gsonBuilder.create();
  }

  static class DateTypeAdapter extends TypeAdapter<Date> {

    private final List<DateFormat> dateFormats = new ArrayList<>();

    public DateTypeAdapter() {
      dateFormats.add(
          DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.US));
      if (!Locale.getDefault().equals(Locale.US)) {
        dateFormats.add(DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT));
      }
    }

    @Override
    public void write(JsonWriter out, Date value) throws IOException {
      if (value == null) {
        out.nullValue();
        return;
      }
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
      String dateFormatAsString = simpleDateFormat.format(value);
      out.value(dateFormatAsString);
    }

    @Override
    public Date read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return deserializeToDate(in.nextString());
    }

    private synchronized Date deserializeToDate(String json) {
      for (DateFormat dateFormat : dateFormats) {
        try {
          return dateFormat.parse(json);
        } catch (ParseException ignored) {
        }
      }
      throw new JsonSyntaxException(json);
    }
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final JsonTemplateGenerator instance;

    Singleton() {
      this.instance = new JsonTemplateGenerator();
    }

    public JsonTemplateGenerator getInstance() {
      return instance;
    }
  }
}
