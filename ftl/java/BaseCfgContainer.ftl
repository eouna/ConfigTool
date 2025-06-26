package ${packageName};

import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.processing.Generated;

import ${beanPackageName}.${baseCfgBean};
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 配置表容器
 *
 * @author CCL
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public abstract class BaseCfgContainer<T extends ${baseCfgBean}> {

  protected Logger logger = LoggerFactory.getLogger(this.getClass());

  // region============================== 模板 =============================
  /** 字段描述列 */
  protected int fieldDescRow = ${fieldInfo.fieldDesc.configBindRow};
  /** 字段类型列 */
  protected int fieldTypeRow = ${fieldInfo.fieldType.configBindRow};
  /** 字段名列 */
  protected int fieldNameRow = ${fieldInfo.fieldName.configBindRow};
  /** 字段数值范围列 */
  protected int fieldDataRangeRow = ${fieldInfo.fieldDataRange.configBindRow};
  /** 数据读取开始行数 */
  protected int dataStartRow = ${dataStartRow};
  // endregion============================== 模板 ==============================

  /** cfgBeanMap key: 配置的ID, 配置的数据 */
  protected Map<Integer, T> cfgBeanMap = Collections.emptyMap();

  /** excel解析异常收集器 */
  protected List<Exception> exceptionCollectors = new CopyOnWriteArrayList<>();

  /** excel文件的md5码 */
  protected Map<String, String> md5CacheMap = Collections.emptyMap();
  /** 数字匹配 */
  private static final Pattern DIGITAL_MATCH = Pattern.compile("\\d*?");

  /** 分隔符 */
  private static final Pattern MAP_LIST_DELIMITER = Pattern.compile("\\{(.)(\\d*?)\\}");

  /** 含义不确定的字符串 */
  private static final Set<Character> DANGLING_CHAR_SET =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList('\\', '?', '{', '}', '(', ')', '.', '+', '-', '*', '^', '$', '|')));

  /**
   * 获取excel文件名 auto gen
   *
   * @return excel文件名
   */
  public abstract List<String> getExcelNameList();

  /**
   * 是否关联其他表形成分表的一部分
   *
   * @return 是否有子表
   */
  public abstract boolean hasRelatedTable();

  /**
   * 是否是父配置表
   *
   * @return 是否是父配置表
   */
  public abstract boolean isParentConfigNode();

  /**
   * 获取新的配置表bean
   *
   * @return cfgBean
   */
  protected abstract T createNewBean();

  /**
   * 创建新的容器
   *
   * @return 新容器
   */
  public abstract BaseCfgContainer<T> getNewContainer();

  public BaseCfgContainer() {}

  /**
   * 加载数据
   *
   * @param resourceRootPath excel资源根路径
   * @throws Exception e
   */
  public synchronized void loadData(String resourceRootPath) throws Exception {
    if (resourceRootPath == null || resourceRootPath.isEmpty()) {
      throw new RuntimeException("bind excel path list is empty");
    }
    // 检查和获取绑定的excel文件
    List<File> excelFileList = checkAndGetExcelFile(resourceRootPath);
    Map<Integer, T> tempCfgMap = new ConcurrentHashMap<>(8);
    Map<String, String> md5HexMap = new ConcurrentHashMap<>(excelFileList.size());
    for (File file : excelFileList) {
      // 给文件的md5赋值
      String md5Hex = DigestUtils.md5Hex(Files.newInputStream(file.toPath()));
      md5HexMap.put(file.getName(), md5Hex);
      // 文件中途发生异常不跳出,继续向后面遍历
      Workbook wb = WorkbookFactory.create(file, null, true);
      Sheet sheet = wb.getSheetAt(0);
      Map<Integer, ExcelFieldInfo> excelFieldInfoMap = loadFieldInfo(sheet, sheet.getRow(0));
      // 获取子类中字段信息
      Map<String, Field> cfgBeanFieldMap = getAllFieldsMap();
      // 单元格跳过列表
      Set<Integer> skipCellList = getSkipCellList(sheet);
      // 检查是否所有字段都在配置表中, 如果没有则抛出异常
      checkExcelFieldMatchBeanField(excelFieldInfoMap, cfgBeanFieldMap, sheet, file);
      // 是否有异常
      boolean hasException = false;
      for (int i = getDataStartRow(); i <= sheet.getLastRowNum(); i++) {
        try {
          T cfgBean = createNewBean();
          Row row = sheet.getRow(i);
          // 检查是否是空行
          if (isBlankRow(row)) {
            continue;
          }
          // 处理单行数据
          handleRowData(row, cfgBean, cfgBeanFieldMap, excelFieldInfoMap, skipCellList);
          // 不能有重复的ID
          if (!tempCfgMap.containsKey(cfgBean.get${idName}())) {
            // 添加bean
            tempCfgMap.put(cfgBean.get${idName}(), cfgBean);
          } else {
            throw new ExcelDataParseException(file.getName(), "出现重复的ID: " + cfgBean.get${idName}());
          }
        } catch (Exception e) {
          // 收集其他的异常
          exceptionCollectors.add(e);
          hasException = true;
        }
      }
      // 有异常直接跳过
      if (!hasException) {
        if (tempCfgMap.isEmpty()) {
          logger.warn("容器{} 加载数据为空", getClass().getSimpleName());
        }
        // 设置为不可变map
        cfgBeanMap = Collections.unmodifiableMap(tempCfgMap);
      }
      // 关闭工作薄
      wb.close();
    }
    if (!exceptionCollectors.isEmpty()) {
      // 将异常列表传递出去
      throw new ContainerExceptionBlocker(exceptionCollectors);
    }
    // 保存md5值
    md5CacheMap = Collections.unmodifiableMap(md5HexMap);
  }

  /**
   * 检查excel字段是否和配置bean中的字段一致
   *
   * @param excelFieldInfoMap excel字段信息
   * @param cfgBeanFieldMap 配置表bean字段
   * @param sheet 工作薄
   * @param file excel文件
   */
  private void checkExcelFieldMatchBeanField(
      Map<Integer, ExcelFieldInfo> excelFieldInfoMap,
      Map<String, Field> cfgBeanFieldMap,
      Sheet sheet,
      File file) {
    // 检查是否所有字段都在配置表中, 如果没有则抛出异常
    List<String> excelFieldList =
        excelFieldInfoMap.values().stream()
            .map(ExcelFieldInfo::getFieldName)
            .collect(Collectors.toList());
    List<String> cfgBeanFieldList = new ArrayList<>(cfgBeanFieldMap.keySet());
    cfgBeanFieldList.removeAll(excelFieldList);
    // 如果配置表中的字段excel中不存在且不是父节点
    if (!cfgBeanFieldList.isEmpty() && !hasRelatedTable()) {
      // 主要是以程序中的字段为主 如果有增删字段需要使用配置表工具重新生成
      throw new ExcelDataParseException(
          sheet.getSheetName(),
          "字段丢失异常, 未在配置表: " + file.getName() + " 找到字段: " + String.join(",", cfgBeanFieldList));
    }
  }

  /**
   * 获取所有字段
   *
   * @return 字段列表
   */
  protected Map<String, Field> getAllFieldsMap() throws ClassNotFoundException {
    ParameterizedType genericInterfaces =
        (ParameterizedType) this.getClass().getGenericSuperclass();
    Type actualTypeArguments = genericInterfaces.getActualTypeArguments()[0];
    final Class<?> baseCfgBean = Class.forName(actualTypeArguments.getTypeName());
    final Map<String, Field> allFields = new HashMap<>(8);
    Class<?> currentClass = baseCfgBean;
    while (currentClass != null) {
      final Field[] declaredFields = currentClass.getDeclaredFields();
      for (Field declaredField : declaredFields) {
        int fieldModifier = declaredField.getModifiers();
        boolean isStaticFinalField =
            Modifier.isStatic(fieldModifier) && Modifier.isFinal(fieldModifier);
        if (!isStaticFinalField) {
          allFields.putIfAbsent(declaredField.getName(), declaredField);
        }
      }
      currentClass = currentClass.getSuperclass();
    }
    return allFields;
  }

  /** 处理单行数据 */
  private void handleRowData(
      Row row,
      T cfgBean,
      Map<String, Field> cfgBeanFieldMap,
      Map<Integer, ExcelFieldInfo> excelFieldInfoMap,
      Set<Integer> skipCellList) {
    List<Integer> colNumList = new ArrayList<>(excelFieldInfoMap.keySet());
    // 遍历单行中每列的数据
    for (int colNum : colNumList) {
      // 需要跳过
      if (skipCellList.contains(colNum)) {
        continue;
      }
      // 读取每列中的类型
      ExcelFieldInfo excelFieldInfo = excelFieldInfoMap.get(colNum);
      Cell cell = row.getCell(colNum);
      // 通过每列的类型解析每列中的数据
      String fieldType = excelFieldInfo.getOriginFieldType();
      // 将解析出的数据赋值给cfgBean中相应的字段
      String fieldName = excelFieldInfo.getFieldName();
      Object cellData;
      if (cell == null) {
        if (colNum == 0) {
          // ID列不能为空
          this.exceptionCollectors.add(
              new ExcelDataParseException(
                  row.getSheet().getSheetName(),
                  "配置表数据解析异常,ID列不能为空, 行: " + (row.getRowNum() + 1) + " 列: 0"));
        }
        FieldDataAdapter fieldDataAdapter = FieldDataAdapter.getFieldAdapterByTypeStr(fieldType);
        // 为空时需要赋予默认值
        cellData = fieldDataAdapter.getFieldAdapter().getDefaultVal();
      } else {
        try {
          // 解析excel中的数据
          cellData = parseCellData(fieldType, cell, cfgBean);
        } catch (Exception e) {
          // 如果此处发生异常说明是配置表格式发生了错误
          this.exceptionCollectors.add(
              new ExcelDataParseException(
                  excelFieldInfo, row, cell, "配置表数据解析异常,请检查单元格数据格式, 异常信息: " + e.getMessage()));
          continue;
        }
      }
      if (colNum == 0 && cellData == null) {
        // ID列不能为空
        this.exceptionCollectors.add(
            new ExcelDataParseException(excelFieldInfo, row, cell, "配置表数据解析异常,ID列不能为空"));
      }
      // 如果配置表中的字段未在bean中找到则跳过,说明是表的新字段
      if (!cfgBeanFieldMap.containsKey(fieldName)) {
        continue;
      }
      Field field = cfgBeanFieldMap.get(fieldName);
      field.setAccessible(true);
      try {
        field.set(cfgBean, cellData);
      } catch (Exception e) {
        // 此处发生异常说明字段不匹配
        this.exceptionCollectors.add(
            new ExcelDataParseException(excelFieldInfo, row, cell, "excel字段和程序字段不匹配,请重新生成java文件"));
      }
    }
  }

  /**
   * 是否跳过当前列
   *
   * @return 是否跳过
   */
  private Set<Integer> getSkipCellList(Sheet sheet) {
    Set<Integer> skipCellList = new HashSet<>();
    Row dataFieldRangeRow = sheet.getRow(getDataStartRow());
    if (dataFieldRangeRow == null || dataFieldRangeRow.getLastCellNum() <= 0) {
      return skipCellList;
    }
    for (Cell cell : dataFieldRangeRow) {
      String cellValue = getCellValue(cell);
      if (!isEmptyString(cellValue) && "${skipStr}".equalsIgnoreCase(cellValue)) {
        skipCellList.add(cell.getColumnIndex());
      }
    }
    return skipCellList;
  }

  /** 解析列数据 */
  protected Object parseCellData(String fieldType, Cell cell, T cfgBean) {
    FieldDataAdapter fieldDataAdapter = FieldDataAdapter.getFieldAdapterByTypeStr(fieldType);
    String cellString = getCellValue(cell);
    return fieldDataAdapter
        .getFieldAdapter()
        .parseFieldStrToClassType(cellString, fieldType, cfgBean);
  }

  public enum FieldDataAdapter {
    // byte
    BYTE(ByteFieldAdapter::new),
    // short
    SHORT(ShortFieldAdapter::new),
    // int
    INT(IntFieldAdapter::new),
    // Long
    LONG(LongFieldAdapter::new),
    // 字符串适配器
    STRING(StringFieldAdapter::new),
    // 浮点数适配器
    FLOAT(FloatFieldAdapter::new),
    // 双精度浮点数适配器
    DOUBLE(DoubleFieldAdapter::new),
    // bool适配器
    BOOLEAN(BooleanFieldAdapter::new),
    // 时间类型的
    Date(DateFieldAdapter::new),
    // 枚举适配器
    ENUM(EnumFieldAdapter::new),
    // List适配器
    LIST(ListFieldAdapter::new),
    // Set适配器
    SET(SetFieldAdapter::new),
    // Map适配器
    MAP(MapFieldAdapter::new),
    ;

    /** 字段适配器 */
    private final FieldAdapter<?> fieldAdapter;

    FieldDataAdapter(Supplier<FieldAdapter<?>> fieldAdapterSupplier) {
      this.fieldAdapter = fieldAdapterSupplier.get();
    }

    public FieldAdapter<?> getFieldAdapter() {
      return fieldAdapter;
    }

    public interface FieldAdapter<K> {

      /**
       * 解析字符串字段为目标数据
       *
       * @param fieldType 字段字符串
       * @param fieldStr 待转换的字符数据
       * @return classType
       */
      K parseFieldStrToClassType(String fieldStr, String fieldType, BaseCfgBean baseCfgBean);

      /**
       * 获取默认值
       *
       * @return 获取默认值
       */
      default K getDefaultVal() {
        return null;
      }

      /**
       * 获取可接受的类型字符串
       *
       * @return 类型字符串数组
       */
      Set<String> getAcceptTypeStr();

      /**
       * 获取转后的目标类型字符串
       *
       * @return 类型字符串
       */
      String getTargetFieldTypeStr(String fieldType);

      /**
       * 是否是基础类型
       *
       * @return 是否是基础类型
       */
      boolean isBaseType();
    }

    private static class ByteFieldAdapter implements FieldAdapter<Byte> {
      @Override
      public Byte parseFieldStrToClassType(String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        return isEmptyString(fieldStr)
            ? getDefaultVal()
            : Byte.parseByte(filterFloatStrVal(fieldStr));
      }

      @Override
      public Byte getDefaultVal() {
        return 0;
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList("[Bb]yte", "java.lang.Byte"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return "byte";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    private static class ShortFieldAdapter implements FieldAdapter<Short> {
      @Override
      public Short parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        return isEmptyString(fieldStr)
            ? getDefaultVal()
            : Short.parseShort(filterFloatStrVal(fieldStr));
      }

      @Override
      public Short getDefaultVal() {
        return 0;
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList("[Ss]hort", "java.lang.Short"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return "short";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    private static class IntFieldAdapter implements FieldAdapter<Integer> {
      @Override
      public Integer parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        return isEmptyString(fieldStr)
            ? getDefaultVal()
            : Integer.parseInt(filterFloatStrVal(fieldStr));
      }

      @Override
      public Integer getDefaultVal() {
        return 0;
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList("[Ii]nt", "[Ii]nteger", "java.lang.Integer"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return "Integer";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    private static class LongFieldAdapter implements FieldAdapter<Long> {
      @Override
      public Long parseFieldStrToClassType(String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        return isEmptyString(fieldStr)
            ? getDefaultVal()
            : Long.parseLong(filterFloatStrVal(fieldStr));
      }

      @Override
      public Long getDefaultVal() {
        return 0L;
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList("[Ll]ong", "java.lang.Long"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return "Long";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    private static class StringFieldAdapter implements FieldAdapter<String> {
      @Override
      public String parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        return isEmptyString(fieldStr.trim()) ? getDefaultVal() : fieldStr;
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList("[Ss]tring", "java.lang.String"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return "String";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    private static class FloatFieldAdapter implements FieldAdapter<Float> {

      @Override
      public Float parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        return isEmptyString(fieldStr) ? getDefaultVal() : Float.parseFloat(fieldStr);
      }

      @Override
      public Float getDefaultVal() {
        return 0F;
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList("[Ff]loat", "java.lang.Float"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return "Float";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    private static class DoubleFieldAdapter implements FieldAdapter<Double> {
      @Override
      public Double parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        return isEmptyString(fieldStr) ? getDefaultVal() : Double.parseDouble(fieldStr);
      }

      @Override
      public Double getDefaultVal() {
        return 0D;
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList("[Dd]ouble", "java.lang.Double"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return "Double";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    private static class DateFieldAdapter implements FieldAdapter<Date> {

      /** 时间匹配 */
      private static final Pattern DATE_PATTERN = Pattern.compile("^[Dd]ate<(.*)>");

      @Override
      public Date parseFieldStrToClassType(String fieldStr, String fieldType, BaseCfgBean baseCfgBean) {
        if (isEmptyString(fieldStr)) {
          return getDefaultVal();
        }
        Matcher matcher = DATE_PATTERN.matcher(fieldType);
        if (!matcher.find()) {
          return getDefaultVal();
        }
        String dateFormat = matcher.group(1);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormat);
        try {
          return simpleDateFormat.parse(fieldStr);
        } catch (ParseException e) {
          throw new ExcelDataParseException(
              "", "时间格式: " + dateFormat + " 和数据源: " + fieldStr + " 不匹配");
        }
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Collections.singletonList(DATE_PATTERN.pattern()));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return "Date";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    private static class BooleanFieldAdapter implements FieldAdapter<Boolean> {
      @Override
      public Boolean parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        return isEmptyString(fieldStr) ? getDefaultVal() : Boolean.parseBoolean(fieldStr);
      }

      @Override
      public Boolean getDefaultVal() {
        return false;
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList("[Bb]oolean", "[Bb]ool", "java.lang.Boolean"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return "Boolean";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    public static class EnumFieldAdapter implements FieldAdapter<Object> {

      /** 枚举匹配 */
      public static final Pattern ENUM_PATTERN =
          Pattern.compile("^(\\w+)\\(((\\w+)((,\\w+)*))*\\)");

      @Override
      public Object parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        String enumClassName = getTargetFieldTypeStr(fieldType);
        Object enumObj = null;
        List<Class<?>> cfgBeanInnerClasses = getAllClassesList(cfgBean.getClass());
        for (Class<?> cfgBeanInnerClass : cfgBeanInnerClasses) {
          if (enumClassName.equals(cfgBeanInnerClass.getSimpleName())) {
            try {
              Method method = cfgBeanInnerClass.getMethod("getEnumByStr", String.class);
              method.setAccessible(true);
              enumObj = method.invoke(null, fieldStr);
              break;
            } catch (NoSuchMethodException
                | SecurityException
                | InvocationTargetException
                | IllegalAccessException exception) {
              throw new ExcelDataParseException(
                  "未在枚举: " + enumClassName + " 中找到方法: getEnumByStr", exception);
            }
          }
        }
        if (enumObj == null) {
          throw new ExcelFormatException("枚举: " + enumClassName + " 中未包含字段: " + fieldStr);
        }
        return enumObj;
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Collections.singletonList(ENUM_PATTERN.pattern()));
      }

      /**
       * 此处直接返回配置的枚举字符串,在获取的时候调用枚举方法
       *
       * @param fieldType 字段类型
       * @return 枚举值
       */
      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        return getEnumClassName(fieldType);
      }

      public String getEnumClassName(String fieldStr) {
        Matcher matcher = ENUM_PATTERN.matcher(fieldStr);
        if (matcher.matches()) {
          if (matcher.groupCount() >= 3) {
            return upperFirst(matcher.group(1));
          }
        }
        return "";
      }

      @Override
      public boolean isBaseType() {
        return true;
      }
    }

    public static class ListFieldAdapter implements FieldAdapter<List<?>> {
      /** 列表匹配 */
      private static final Pattern LIST_PATTERN = Pattern.compile("^[Ll]ist<(.*)>(\\{.\\d*\\})");

      @Override
      public List<?> parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        // 需要检查分隔符是否有重复的情况
        checkListMapFieldTypeDelimiter(fieldType);
        List<Object> list = new ArrayList<>();
        if (isEmptyString(fieldStr)) {
          return getDefaultVal();
        }
        Matcher matcher = LIST_PATTERN.matcher(fieldType);
        if (matcher.find()) {
          String subType = matcher.group(1);
          String delimiterWithBracket = matcher.group(2);
          BracketMetadata delimiter = getBracketInnerChar(delimiterWithBracket);
          FieldDataAdapter fieldDataAdapter = getFieldAdapterByTypeStr(subType);
          // 替换可能有正则的表达式字符串
          String replaceSplitDanglingMetaChar =
              replaceSplitDanglingMetaChar(delimiter.delimiterChar);
          String[] listValSplit = fieldStr.split(replaceSplitDanglingMetaChar);
          if (delimiter.sizeLimit > 0 && listValSplit.length > delimiter.sizeLimit) {
            throw new ExcelDataParseException(
                "", "字段对应的数据数量: " + listValSplit.length + " 超过限制值: " + delimiter.sizeLimit);
          }
          for (String listVal : listValSplit) {
            if (isEmptyString(listVal)) {
              continue;
            }
            list.add(
                fieldDataAdapter
                    .getFieldAdapter()
                    .parseFieldStrToClassType(listVal, subType, cfgBean));
          }
        }
        return Collections.unmodifiableList(list);
      }

      @Override
      public List<?> getDefaultVal() {
        return Collections.emptyList();
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList(LIST_PATTERN.pattern(), "java.util.List"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        Matcher matcher = LIST_PATTERN.matcher(fieldType);
        String listTypeStr = null;
        if (matcher.find()) {
          String subTypeWithBracket = matcher.group(1);
          String delimiterWithBracket = matcher.group(2);
          // 去除list分隔符的类型
          listTypeStr = fieldType.replace(delimiterWithBracket, "");
          FieldDataAdapter fieldDataAdapter =
              FieldDataAdapter.getFieldAdapterByTypeStr(subTypeWithBracket);
          String subType = fieldDataAdapter.getFieldAdapter().getTargetFieldTypeStr(listTypeStr);
          listTypeStr = listTypeStr.replace(subTypeWithBracket, subType);
        }
        return listTypeStr;
      }

      @Override
      public boolean isBaseType() {
        return false;
      }
    }

    public static class SetFieldAdapter implements FieldAdapter<Set<?>> {
      /** set匹配 */
      private static final Pattern SET_PATTERN = Pattern.compile("^[Ss]et<(.*)>(\\{.\\d*\\})");

      @Override
      public Set<?> parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        // 需要检查分隔符是否有重复的情况
        checkListMapFieldTypeDelimiter(fieldType);
        if (isEmptyString(fieldStr)) {
          return getDefaultVal();
        }
        Set<Object> set = new HashSet<>();
        Matcher matcher = SET_PATTERN.matcher(fieldType);
        if (matcher.find()) {
          String subType = matcher.group(1);
          String delimiterWithBracket = matcher.group(2);
          BracketMetadata bracketMetadata = getBracketInnerChar(delimiterWithBracket);
          FieldDataAdapter fieldDataAdapter = getFieldAdapterByTypeStr(subType);
          // 替换可能有正则的表达式字符串
          String replaceSplitDanglingMetaChar =
              replaceSplitDanglingMetaChar(bracketMetadata.delimiterChar);
          String[] setValSplit = fieldStr.split(replaceSplitDanglingMetaChar);
          if (bracketMetadata.sizeLimit > 0 && setValSplit.length > bracketMetadata.sizeLimit) {
            throw new ExcelDataParseException(
                "", "字段对应的数据数量: " + setValSplit.length + " 超过限制值: " + bracketMetadata.sizeLimit);
          }
          for (String setVal : setValSplit) {
            if (isEmptyString(setVal)) {
              continue;
            }
            Object data =
                fieldDataAdapter
                    .getFieldAdapter()
                    .parseFieldStrToClassType(setVal, subType, cfgBean);
            if (!set.add(data)) {
              throw new ExcelDataParseException("", "Set列数据出现重复数据: " + data);
            }
          }
        }
        return Collections.unmodifiableSet(set);
      }

      @Override
      public Set<?> getDefaultVal() {
        return Collections.emptySet();
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList(SET_PATTERN.pattern(), "java.util.Set"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        Matcher matcher = SET_PATTERN.matcher(fieldType);
        String setTypeStr = null;
        if (matcher.find()) {
          // 需要检查分隔符是否有重复的情况
          checkListMapFieldTypeDelimiter(fieldType);
          String subTypeWithBracket = matcher.group(1);
          String delimiterWithBracket = matcher.group(2);
          // 去除set分隔符的类型
          setTypeStr = fieldType.replace(delimiterWithBracket, "");
          FieldDataAdapter fieldDataAdapter = getFieldAdapterByTypeStr(subTypeWithBracket);
          String subType =
              fieldDataAdapter.getFieldAdapter().getTargetFieldTypeStr(subTypeWithBracket);
          setTypeStr = setTypeStr.replace(subTypeWithBracket, subType);
          setTypeStr = upperFirst(setTypeStr);
        }
        return setTypeStr;
      }

      @Override
      public boolean isBaseType() {
        return false;
      }
    }

    public static class MapFieldAdapter implements FieldAdapter<Map<?, ?>> {

      /** map表达式 */
      private static final Pattern MAP_PATTERN =
          Pattern.compile("^[Mm]ap<(\\w+(\\(\\))?)(\\{(.)\\})(.*)>(\\{.\\d*\\})");

      /**
       * 解析思路 Map 需要两个符号进行进行分隔,如何表达出两个分隔符是关键
       *
       * @param fieldStr 待转换的字符数据
       * @param fieldType 字段字符串
       * @return 解析后的字段
       */
      @Override
      public Map<?, ?> parseFieldStrToClassType(
          String fieldStr, String fieldType, BaseCfgBean cfgBean) {
        Map<Object, Object> map = new LinkedHashMap<>(8);
        // 需要检查分隔符是否有重复的情况
        checkListMapFieldTypeDelimiter(fieldType);
        if (isEmptyString(fieldStr)) {
          return getDefaultVal();
        }
        Matcher matcher = MAP_PATTERN.matcher(fieldType);
        if (matcher.find()) {

          String keySubType = matcher.group(1);
          String keyValDelimiterWithBracket = matcher.group(3);
          BracketMetadata keyValDelimiter = getBracketInnerChar(keyValDelimiterWithBracket);

          String valType = matcher.group(5);
          String mapDelimiterWithBracket = matcher.group(6);
          BracketMetadata mapDelimiter = getBracketInnerChar(mapDelimiterWithBracket);

          FieldDataAdapter keyFieldDataAdapter = getFieldAdapterByTypeStr(keySubType);
          if (keyFieldDataAdapter.getFieldAdapter() instanceof BooleanFieldAdapter) {
            throw new ExcelFormatException("解析类型错误Map类型字段的key值无法使用布尔类型");
          }
          if (!keyFieldDataAdapter.getFieldAdapter().isBaseType()) {
            throw new ExcelFormatException(
                "Map类型解析错误,key不为基础类型(int,long,float,double,String) 当前类型: " + keySubType);
          }
          String replaceSplitDanglingMetaChar =
              replaceSplitDanglingMetaChar(mapDelimiter.delimiterChar);
          String[] mapArr = fieldStr.split(replaceSplitDanglingMetaChar);
          if (mapDelimiter.sizeLimit > 0 && mapArr.length > mapDelimiter.sizeLimit) {
            throw new ExcelDataParseException(
                "", "字段对应的数据数量: " + mapArr.length + " 超过限制值: " + mapDelimiter.sizeLimit);
          }
          for (String mapStr : mapArr) {
            if (isEmptyString(mapStr)) {
              continue;
            }
            replaceSplitDanglingMetaChar =
                replaceSplitDanglingMetaChar(keyValDelimiter.delimiterChar);
            String[] keyValArr = mapStr.split(replaceSplitDanglingMetaChar);
            // key原始值
            String keyStr = keyValArr[0];
            // key解析后的值
            Object keyParsed =
                keyFieldDataAdapter
                    .getFieldAdapter()
                    .parseFieldStrToClassType(keyStr, keySubType, cfgBean);
            // val原始值
            String valStr = keyValArr[1];
            FieldDataAdapter valFieldDataAdapter = getFieldAdapterByTypeStr(valType);
            // val解析后的值
            Object valParsed =
                valFieldDataAdapter
                    .getFieldAdapter()
                    .parseFieldStrToClassType(valStr, valType, cfgBean);
            map.put(keyParsed, valParsed);
          }
        }
        return Collections.unmodifiableMap(map);
      }

      @Override
      public Map<?, ?> getDefaultVal() {
        return Collections.emptyMap();
      }

      @Override
      public Set<String> getAcceptTypeStr() {
        return new HashSet<>(Arrays.asList(MAP_PATTERN.pattern(), "java.util.map"));
      }

      @Override
      public String getTargetFieldTypeStr(String fieldType) {
        Matcher matcher = MAP_PATTERN.matcher(fieldType);
        String mapTypeStr = "";
        if (matcher.find()) {

          String keySubType = matcher.group(1);
          String keyValDelimiterWithBracket = matcher.group(3);

          String valTypeStr = matcher.group(5);
          String mapDelimiterWithBracket = matcher.group(6);

          // 转换key类型
          FieldDataAdapter keyFieldDataAdapter = getFieldAdapterByTypeStr(keySubType);
          String keyType = keyFieldDataAdapter.getFieldAdapter().getTargetFieldTypeStr(keySubType);
          keySubType = replaceSplitDanglingMetaChar(keySubType);
          // 转换完成需要添加','
          mapTypeStr = fieldType.replaceFirst("[Mm]ap<" + keySubType, "Map<" + keyType + ",");

          // 转换val类型
          FieldDataAdapter valTypeFieldDataAdapter = getFieldAdapterByTypeStr(valTypeStr);
          String valType =
              valTypeFieldDataAdapter.getFieldAdapter().getTargetFieldTypeStr(valTypeStr);
          mapTypeStr = mapTypeStr.replace(valTypeStr, valType);

          // 消除key-val之间以及map之间的分隔符
          mapTypeStr =
              mapTypeStr
                  .replace(keyValDelimiterWithBracket, "")
                  .replace(mapDelimiterWithBracket, "");
        }
        return mapTypeStr;
      }

      @Override
      public boolean isBaseType() {
        return false;
      }
    }

    private static final Map<FieldDataAdapter, List<Pattern>> PATTERN_CACHE_MAP =
        new ConcurrentHashMap<>();

    /**
     * 查找类型字段
     *
     * @param typeStr 类型字符串
     * @return 目标类型字段
     */
    public static FieldDataAdapter getFieldAdapterByTypeStr(String typeStr) {
      // 将检查字符串左右进行除空字符串处理
      final String finalTypeStr = typeStr.trim();
      List<FieldDataAdapter> adapters = getFieldDataAdapterList();
      for (FieldDataAdapter value : adapters) {
        if (!PATTERN_CACHE_MAP.containsKey(value)) {
          Set<String> matchStr = value.getFieldAdapter().getAcceptTypeStr();
          PATTERN_CACHE_MAP.computeIfAbsent(
              value, k -> matchStr.stream().map(Pattern::compile).collect(Collectors.toList()));
        }
        if (PATTERN_CACHE_MAP.get(value).stream()
            .anyMatch(pattern -> pattern.matcher(finalTypeStr).matches())) {
          return value;
        }
      }
      throw new ExcelFormatException("typeStr: " + typeStr + " cannot found any field type match");
    }

    private static List<FieldDataAdapter> getFieldDataAdapterList() {
      // 枚举类型需要放在最后处理，否则list和map就无法嵌套枚举类型
      return Arrays.stream(values())
          .sorted(
              (o1, o2) -> {
                if (o1 == FieldDataAdapter.ENUM) {
                  return 1;
                }
                if (o2 == FieldDataAdapter.ENUM) {
                  return 1;
                }
                return o1.getFieldAdapter()
                    .getClass()
                    .getSimpleName()
                    .compareTo(o2.getFieldAdapter().getClass().getSimpleName());
              })
          .collect(Collectors.toList());
    }

    /**
     * 检查list和map类型的分隔是否只出现过一次
     *
     * @param fieldType 字段类型
     */
    private static void checkListMapFieldTypeDelimiter(String fieldType) {
      Matcher matcher = MAP_LIST_DELIMITER.matcher(fieldType);
      Set<String> delimiter = new HashSet<>();
      while (matcher.find()) {
        String group = matcher.group(1);
        if (!isEmptyString(group)) {
          if (!delimiter.add(group)) {
            throw new ExcelFormatException("列表或Map分隔符使用错误,出现重复的分隔符: " + group);
          }
        } else {
          throw new ExcelFormatException("列表或Map分隔符使用错误,不能使用空格分隔符");
        }
      }
    }

    private static String filterFloatStrVal(String floatValStr) {
      String trimStr = floatValStr.trim();
      return isEmptyString(trimStr)
          ? "0"
          : (floatValStr.indexOf(".") > 0
              ? trimStr.substring(0, floatValStr.indexOf("."))
              : trimStr);
    }

    /**
     * 替换分隔时不确定的字符如 /+|.+?{}()等等
     *
     * @return 替换后的字符
     */
    private static String replaceSplitDanglingMetaChar(String splitChar) {
      char[] chars = splitChar.toCharArray();
      StringBuilder parsedSplitChar = new StringBuilder();
      for (char aChar : chars) {
        parsedSplitChar.append(DANGLING_CHAR_SET.contains(aChar) ? "\\" + aChar : aChar + "");
      }
      return parsedSplitChar.toString();
    }

    static class BracketMetadata {
      String delimiterChar;
      int sizeLimit;
    }

    /** 获取括号内的字符 */
    private static BracketMetadata getBracketInnerChar(String strWithBracket) {
      Matcher mapDelimiterMatcher = MAP_LIST_DELIMITER.matcher(strWithBracket);
      BracketMetadata bracketMetadata = new BracketMetadata();
      if (mapDelimiterMatcher.find()) {
        bracketMetadata.delimiterChar = mapDelimiterMatcher.group(1);
        String sizeLimit = mapDelimiterMatcher.group(2);
        if (!isEmptyString(sizeLimit) && DIGITAL_MATCH.matcher(sizeLimit).matches()) {
          bracketMetadata.sizeLimit = Integer.parseInt(sizeLimit);
        }
      }
      return bracketMetadata;
    }

    /**
     * 小写第一个字符
     *
     * @param str 待转换的字符串
     * @return 转换后字符串
     */
    public static String upperFirst(String str) {
      char[] charArray = str.toCharArray();
      char lowerCharStart = 'a', lowerCharEnd = 'z';
      if (charArray[0] <= lowerCharEnd && charArray[0] >= lowerCharStart) {
        charArray[0] -= 32;
        return new String(charArray);
      }
      return str;
    }
  }

  /** 检测空行 */
  private boolean isBlankRow(Row row) {
    if (row == null) {
      return true;
    }
    for (Cell cell : row) {
      if (cell != null && cell.getCellType() != CellType.BLANK) {
        return false;
      }
    }
    return true;
  }

  /**
   * 加载字段信息
   *
   * @param sheet 表信息
   * @param row 当前行信息
   * @return 字段信息
   */
  private Map<Integer, ExcelFieldInfo> loadFieldInfo(Sheet sheet, Row row) {
    Map<Integer, ExcelFieldInfo> excelFieldInfoMap = new HashMap<>(8);
    // 装载头部数据
    for (int colNum = 0; colNum <= row.getLastCellNum(); colNum++) {
      ExcelFieldInfo excelFieldInfo = new ExcelFieldInfo();
      // 加载字段类型
      excelFieldInfo.setOriginFieldType(loadFieldInfoData(sheet, colNum, getFieldTypeRow()));
      if (isEmptyString(excelFieldInfo.getOriginFieldType())) {
        continue;
      }
      // 加载字段名
      excelFieldInfo.setFieldName(loadFieldInfoData(sheet, colNum, getFieldNameRow()));
      if (isEmptyString(excelFieldInfo.getFieldName())) {
        continue;
      }
      excelFieldInfoMap.put(colNum, excelFieldInfo);
    }
    return excelFieldInfoMap;
  }

  /** 字符是否为空 */
  private static Boolean isEmptyString(String checkEmptyStr) {
    return checkEmptyStr == null || checkEmptyStr.trim().isEmpty();
  }

  /**
   * 获取目标类及父类所有的子类
   *
   * @param cls class
   * @return 所有字段
   */
  public static List<Class<?>> getAllClassesList(Class<?> cls) {
    Validate.notNull(cls, "cls", new Object[0]);
    List<Class<?>> allFields = new ArrayList<>();
    for (Class<?> currentClass = cls;
        currentClass != null;
        currentClass = currentClass.getSuperclass()) {
      Class<?>[] declaredFields = currentClass.getDeclaredClasses();
      Collections.addAll(allFields, declaredFields);
    }
    return allFields;
  }

  /**
   * 加载单列字段信息
   *
   * @param sheet 工作簿
   * @param colNum 行号
   */
  private String loadFieldInfoData(Sheet sheet, int colNum, int rowNum) {
    if (rowNum < 0) {
      throw new ExcelDataParseException(
          sheet.getSheetName(),
          "加载字段信息错误 列数: " + (colNum + 1) + " 配置表名: " + sheet.getSheetName() + " 配置的行数小于0");
    }
    if (sheet.getRow(rowNum) != null) {
      Cell cell = sheet.getRow(rowNum).getCell(colNum);
      String cellVal = null;
      if (cell != null) {
        String cellValue = getCellValue(cell);
        cellVal = cellValue.trim();
      }
      if (colNum == 0 && isEmptyString(cellVal)) {
        // 兼容第一行不填的情况默认为int
        cellVal = "int";
      }
      return cellVal;
    }
    return null;
  }

  /**
   * 将工作单元中都转为字符串进行处理
   *
   * @param cell 工作单元
   * @return 字符串
   */
  public static String getCellValue(Cell cell) {
    String value;
    switch (cell.getCellType()) {
        // 字符串
      case STRING:
        value = cell.getStringCellValue();
        break;
        // Boolean
      case BOOLEAN:
        value = cell.getBooleanCellValue() + "";
        break;
        // 数字
      case NUMERIC:
        if (String.valueOf(cell.getNumericCellValue()).contains("E")) {
          DataFormatter dataFormatter = new DataFormatter();
          return dataFormatter.formatCellValue(cell);
        }
        value = cell.getNumericCellValue() + "";
        break;
        // 公式
      case FORMULA:
        FormulaEvaluator formulaEvaluator =
            cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
        CellValue cellValue = formulaEvaluator.evaluate(cell);
        switch (cellValue.getCellType()) {
            // 字符串
          case STRING:
            value = cellValue.getStringValue();
            break;
            // Boolean
          case BOOLEAN:
            value = cellValue.getBooleanValue() + "";
            break;
            // 公式
          case NUMERIC:
            if (String.valueOf(cell.getNumericCellValue()).contains("E")) {
              DataFormatter dataFormatter = new DataFormatter();
              return dataFormatter.formatCellValue(cell);
            }
            value = cell.getNumericCellValue() + "";
            break;
          // 空值
          case BLANK:
          default:
            value = "";
            break;
        }
        break;
        // 空值
      case BLANK:
      default:
        value = "";
        break;
    }
    return value;
  }

  /**
   * 检查文件是否存在或者文件名是否正确
   *
   * @return 检查完后的excel
   */
  private List<File> checkAndGetExcelFile(String resourceRootPath) {
    List<File> excelFileList = new ArrayList<>();
    List<String> bindExcelPathList = getBindExcelPathList(resourceRootPath);
    for (String filePathName : bindExcelPathList) {
      File file = new File(filePathName);
      boolean isWrongFile = !file.exists() || file.isDirectory();
      boolean isWrongFileSuffix = !file.getName().contains(".xls");
      if (isWrongFile || isWrongFileSuffix) {
        continue;
      }
      excelFileList.add(file);
    }
    return excelFileList;
  }

  /**
   * 获取绑定的excel
   *
   * @return excel名列表
   */
  protected List<String> getBindExcelPathList(String resourceRootPath) {
    return getExcelNameList().stream()
        .filter(excelName -> excelName != null && !excelName.isEmpty())
        .map(
            excelName -> {
              String excelNameReplaceSeparator = excelName.replace("\\", File.separator);
              return resourceRootPath + File.separator + excelNameReplaceSeparator;
            })
        .collect(Collectors.toList());
  }

  public static class ExcelFormatException extends RuntimeException {

    @java.io.Serial
    static final long serialVersionUID = 475818434121545L;

    public ExcelFormatException(String message) {
      super(message);
    }
  }

  public static class ExcelDataParseException extends RuntimeException {

    @java.io.Serial
    static final long serialVersionUID = 1574124684235L;

    final String excelFileName;
    ExcelFieldInfo excelFieldInfo;
    Row row;
    Cell cell;

    public ExcelDataParseException(String file, String message) {
      super(message);
      this.excelFileName = file;
    }

    public ExcelDataParseException(String file, Throwable e) {
      super(e);
      this.excelFileName = file;
    }

    public ExcelDataParseException(ExcelFieldInfo excelFieldInfo, Row row, Cell cell, String msg) {
      super(msg);
      this.excelFileName = row.getSheet().getSheetName();
      this.excelFieldInfo = excelFieldInfo;
      this.row = row;
      this.cell = cell;
    }

    public ExcelDataParseException(ExcelFieldInfo excelFieldInfo, Row row, Cell cell, Throwable e) {
      super(e);
      this.excelFileName = row.getSheet().getSheetName();
      this.excelFieldInfo = excelFieldInfo;
      this.row = row;
      this.cell = cell;
    }

    @Override
    public String getMessage() {
      if (row != null && cell != null && excelFieldInfo != null) {
        return "解析文件: "
            + row.getSheet().getSheetName()
            + " 行: "
            + (row.getRowNum() + 1)
            + " 列: "
            + (cell.getColumnIndex() + 1)
            + " 字段名: "
            + excelFieldInfo.getFieldName()
            + " 字段类型: "
            + excelFieldInfo.getOriginFieldType()
            + " 数据: "
            + getCellValue(cell)
            + " 异常: "
            + super.getMessage();
      } else if (!isEmptyString(excelFileName)) {
        return "解析文件: " + excelFileName + " 时发生异常:" + super.getMessage();
      } else {
        return "发生异常:" + super.getMessage();
      }
    }
  }

  /** 容器异常传递 */
  public static class ContainerExceptionBlocker extends Exception {

    @java.io.Serial
    static final long serialVersionUID = 5785314693145L;

    private final List<Exception> exceptions;

    public ContainerExceptionBlocker(List<Exception> exceptions) {
      this.exceptions = exceptions;
    }

    public List<Exception> getExceptions() {
      return exceptions;
    }
  }

  /** excel 字段信息 */
  public static class ExcelFieldInfo {

    private String fieldName;

    private String originFieldType;

    public String getFieldName() {
      return fieldName;
    }

    public void setFieldName(String fieldName) {
      this.fieldName = fieldName;
    }

    public String getOriginFieldType() {
      return originFieldType;
    }

    public void setOriginFieldType(String originFieldType) {
      this.originFieldType = originFieldType;
    }
  }

  public Map<Integer, T> getCfgBeanMap() {
    return Collections.unmodifiableMap(cfgBeanMap);
  }

  public List<Exception> getExceptionCollectors() {
    return exceptionCollectors;
  }

  public List<T> getCfgBeanList() {
    return Collections.unmodifiableList(new ArrayList<>(cfgBeanMap.values()));
  }

  public Map<String, String> getMd5CacheMap() {
    return Collections.unmodifiableMap(md5CacheMap);
  }

  public int getFieldTypeRow() {
    return fieldTypeRow;
  }

  public int getFieldNameRow() {
    return fieldNameRow;
  }

  public int getFieldDataRangeRow() {
    return fieldDataRangeRow;
  }

  public int getDataStartRow() {
    return dataStartRow;
  }
}
