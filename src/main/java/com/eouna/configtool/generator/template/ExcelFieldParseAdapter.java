package com.eouna.configtool.generator.template;

import com.eouna.configtool.constant.StrConstant;
import com.eouna.configtool.exceptions.ExcelDataParseException;
import com.eouna.configtool.generator.exceptions.ExcelFormatCheckException;
import com.eouna.configtool.utils.StrUtils;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * excel字段对应的数据解析适配器
 *
 * @author CCL
 */
public enum ExcelFieldParseAdapter {
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
  private final IFieldAdapter<?> fieldAdapter;

  /** 数字匹配 */
  private static final Pattern DIGITAL_MATCH = Pattern.compile("\\d*?");

  /** 分隔符 */
  private static final Pattern BRACKET_DELIMITER_PATTERN = Pattern.compile("\\{(.)(\\d*?)\\}");

  /** 含义不确定的字符串 */
  private static final Set<Character> DANGLING_CHAR_SET =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList('\\', '?', '{', '}', '(', ')', '.', '+', '-', '*', '^', '$', '|')));

  ExcelFieldParseAdapter(Supplier<IFieldAdapter<?>> fieldAdapterSupplier) {
    this.fieldAdapter = fieldAdapterSupplier.get();
  }

  public IFieldAdapter<?> getFieldAdapter() {
    return fieldAdapter;
  }

  private static class ByteFieldAdapter implements IFieldAdapter<Byte> {
    @Override
    public Byte parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      return StringUtils.isEmpty(fieldStr)
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
    public String getTargetFieldObjTypeStr(String fieldType) {
      return "Byte";
    }

    @Override
    public boolean isBaseType() {
      return true;
    }
  }

  private static class ShortFieldAdapter implements IFieldAdapter<Short> {
    @Override
    public Short parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      return StringUtils.isEmpty(fieldStr)
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
    public String getTargetFieldObjTypeStr(String fieldType) {
      return "Short";
    }

    @Override
    public boolean isBaseType() {
      return true;
    }
  }

  private static class IntFieldAdapter implements IFieldAdapter<Integer> {
    @Override
    public Integer parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      return StringUtils.isEmpty(fieldStr)
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
      return "int";
    }

    @Override
    public String getTargetFieldObjTypeStr(String fieldType) {
      return "Integer";
    }

    @Override
    public boolean isBaseType() {
      return true;
    }
  }

  private static class LongFieldAdapter implements IFieldAdapter<Long> {
    @Override
    public Long parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      return StringUtils.isEmpty(fieldStr)
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
      return "long";
    }

    @Override
    public String getTargetFieldObjTypeStr(String fieldType) {
      return "Long";
    }

    @Override
    public boolean isBaseType() {
      return true;
    }
  }

  private static class StringFieldAdapter implements IFieldAdapter<String> {
    @Override
    public String parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      return StringUtils.isEmpty(fieldStr.trim()) ? getDefaultVal() : fieldStr;
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

  private static class FloatFieldAdapter implements IFieldAdapter<Float> {

    @Override
    public Float parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      return StringUtils.isEmpty(fieldStr) ? getDefaultVal() : Float.parseFloat(fieldStr);
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
      return "float";
    }

    @Override
    public String getTargetFieldObjTypeStr(String fieldType) {
      return "Float";
    }

    @Override
    public boolean isBaseType() {
      return true;
    }
  }

  private static class DoubleFieldAdapter implements IFieldAdapter<Double> {
    @Override
    public Double parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      return StringUtils.isEmpty(fieldStr) ? getDefaultVal() : Double.parseDouble(fieldStr);
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
      return "double";
    }

    @Override
    public String getTargetFieldObjTypeStr(String fieldType) {
      return "Double";
    }

    @Override
    public boolean isBaseType() {
      return true;
    }
  }

  private static class BooleanFieldAdapter implements IFieldAdapter<Boolean> {
    @Override
    public Boolean parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      return StringUtils.isEmpty(fieldStr) ? getDefaultVal() : Boolean.parseBoolean(fieldStr);
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
      return "boolean";
    }

    @Override
    public String getTargetFieldObjTypeStr(String fieldType) {
      return "Boolean";
    }

    @Override
    public boolean isBaseType() {
      return true;
    }
  }

  private static class DateFieldAdapter implements IFieldAdapter<Date> {

    /** 时间匹配 */
    private static final Pattern DATE_PATTERN = Pattern.compile("^[Dd]ate<(.*)>");

    @Override
    public Date parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      if (StringUtils.isEmpty(fieldStr)) {
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
    public String getTargetFieldObjTypeStr(String fieldType) {
      return "Date";
    }

    @Override
    public boolean isBaseType() {
      return true;
    }
  }

  public static class EnumFieldAdapter implements IFieldAdapter<String> {

    /** 枚举匹配 */
    public static final Pattern ENUM_PATTERN = Pattern.compile("^(\\w+)\\(((\\w+)((,\\w+)*))*\\)");

    private static final int MAX_MATCH_SIZE = 4;

    @Override
    public String parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      return StringUtils.isEmpty(fieldStr.trim()) ? getDefaultVal() : fieldStr;
    }

    @Override
    public Set<String> getAcceptTypeStr() {
      // \w+ 可使list和map支持枚举类型，但是在匹配时必须放最后
      return new HashSet<>(Arrays.asList(ENUM_PATTERN.pattern(), "\\w+"));
    }

    public String getEnumClassName(String fieldStr) {
      Matcher matcher = ENUM_PATTERN.matcher(fieldStr);
      if (matcher.matches()) {
        if (matcher.groupCount() >= MAX_MATCH_SIZE) {
          return StrUtils.upperFirst(matcher.group(1));
        }
      }
      throw new ExcelFormatCheckException("枚举类型的字段" + fieldStr + "必须满足 A(a1,a2,...)或者A() 格式");
    }

    /**
     * 获取枚举数据内的字段列表
     *
     * @return 字段列表
     */
    public Set<String> getEnumFieldSet(String fieldStr) {
      Matcher matcher = ENUM_PATTERN.matcher(fieldStr);
      if (matcher.matches()) {
        Set<String> fieldList = new HashSet<>();
        fieldList.add(matcher.group(3));
        if (matcher.groupCount() >= MAX_MATCH_SIZE) {
          String fieldListStr = matcher.group(4);
          String[] fieldArr = fieldListStr.split(",");
          if (fieldListStr.length() == 1) {
            fieldList.add(fieldArr[0]);
          } else {
            fieldList.addAll(
                Arrays.stream(fieldArr)
                    .filter(StringUtils::isNoneEmpty)
                    .collect(Collectors.toList()));
          }
        }
        return fieldList;
      }
      return Collections.emptySet();
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

    @Override
    public String getTargetFieldObjTypeStr(String fieldType) {
      return "String";
    }

    @Override
    public boolean isBaseType() {
      return true;
    }
  }

  public static class ListFieldAdapter implements IFieldAdapter<List<?>> {
    /** 列表匹配 */
    private static final Pattern LIST_PATTERN = Pattern.compile("^[Ll]ist<(.*)>(\\{.\\d*\\})");

    @Override
    public List<?> parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      // 需要检查分隔符是否有重复的情况
      checkListMapFieldTypeDelimiter(fieldType);
      List<Object> list = new ArrayList<>();
      if (StringUtils.isEmpty(fieldStr)) {
        return getDefaultVal();
      }
      Matcher matcher = LIST_PATTERN.matcher(fieldType);
      if (matcher.find()) {
        String subType = matcher.group(1);
        String delimiterWithBracket = matcher.group(2);
        BracketMetadata bracketMetadata = getBracketInnerChar(delimiterWithBracket);
        ExcelFieldParseAdapter fieldDataAdapter = getFieldAdapterByTypeStr(subType);
        // 替换可能有正则的表达式字符串
        String replaceSplitDanglingMetaChar =
            replaceSplitDanglingMetaChar(bracketMetadata.delimiterChar);
        String[] listValSplit = fieldStr.split(replaceSplitDanglingMetaChar);
        if (bracketMetadata.sizeLimit > 0 && listValSplit.length > bracketMetadata.sizeLimit) {
          throw new ExcelDataParseException(
              "", "字段对应的数据数量: " + listValSplit.length + " 超过限制值: " + bracketMetadata.sizeLimit);
        }
        for (String listVal : listValSplit) {
          if (StrUtils.isEmpty(listVal)) {
            continue;
          }
          list.add(
              fieldDataAdapter.getFieldAdapter().parseFiledStrToJavaClassType(listVal, subType));
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
      return new HashSet<>(Collections.singletonList(LIST_PATTERN.pattern()));
    }

    @Override
    public String getTargetFieldTypeStr(String fieldType) {
      Matcher matcher = LIST_PATTERN.matcher(fieldType);
      String listTypeStr = null;
      if (matcher.find()) {
        // 需要检查分隔符是否有重复的情况
        checkListMapFieldTypeDelimiter(fieldType);
        String subTypeWithBracket = matcher.group(1);
        String delimiterWithBracket = matcher.group(2);
        // 去除list分隔符的类型
        listTypeStr = fieldType.replace(delimiterWithBracket, "");
        ExcelFieldParseAdapter fieldDataAdapter =
            ExcelFieldParseAdapter.getFieldAdapterByTypeStr(subTypeWithBracket);
        String subType =
            fieldDataAdapter.getFieldAdapter().getTargetFieldObjTypeStr(subTypeWithBracket);
        listTypeStr = listTypeStr.replace(subTypeWithBracket, subType);
        listTypeStr = StrUtils.upperFirst(listTypeStr);
      }
      return listTypeStr;
    }

    @Override
    public boolean isBaseType() {
      return false;
    }
  }

  public static class SetFieldAdapter implements IFieldAdapter<Set<?>> {
    /** set匹配 */
    private static final Pattern SET_PATTERN = Pattern.compile("^[Ss]et<(.*)>(\\{.\\d*\\})");

    @Override
    public Set<?> parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      // 需要检查分隔符是否有重复的情况
      checkListMapFieldTypeDelimiter(fieldType);
      if (StringUtils.isEmpty(fieldStr)) {
        return getDefaultVal();
      }
      Set<Object> set = new HashSet<>();
      Matcher matcher = SET_PATTERN.matcher(fieldType);
      if (matcher.find()) {
        String subType = matcher.group(1);
        String delimiterWithBracket = matcher.group(2);
        BracketMetadata bracketMetadata = getBracketInnerChar(delimiterWithBracket);
        ExcelFieldParseAdapter fieldDataAdapter = getFieldAdapterByTypeStr(subType);
        // 替换可能有正则的表达式字符串
        String replaceSplitDanglingMetaChar =
            replaceSplitDanglingMetaChar(bracketMetadata.delimiterChar);
        String[] setValSplit = fieldStr.split(replaceSplitDanglingMetaChar);
        if (bracketMetadata.sizeLimit > 0 && setValSplit.length > bracketMetadata.sizeLimit) {
          throw new ExcelDataParseException(
              "", "字段对应的数据数量: " + setValSplit.length + " 超过限制值: " + bracketMetadata.sizeLimit);
        }
        for (String setVal : setValSplit) {
          if (StrUtils.isEmpty(setVal)) {
            continue;
          }
          Object data =
              fieldDataAdapter.getFieldAdapter().parseFiledStrToJavaClassType(setVal, subType);
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
      return new HashSet<>(Collections.singletonList(SET_PATTERN.pattern()));
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
        ExcelFieldParseAdapter fieldDataAdapter =
            ExcelFieldParseAdapter.getFieldAdapterByTypeStr(subTypeWithBracket);
        String subType =
            fieldDataAdapter.getFieldAdapter().getTargetFieldObjTypeStr(subTypeWithBracket);
        setTypeStr = setTypeStr.replace(subTypeWithBracket, subType);
        setTypeStr = StrUtils.upperFirst(setTypeStr);
      }
      return setTypeStr;
    }

    @Override
    public boolean isBaseType() {
      return false;
    }
  }

  public static class MapFieldAdapter implements IFieldAdapter<Map<?, ?>> {

    /** map表达式 */
    private static final Pattern MAP_PATTERN =
        Pattern.compile("^[Mm]ap<(\\w+(\\(\\))?)(\\{(.)\\})(.*)>(\\{.\\d*\\})");

    /**
     * 解析Map字段成Java类字段
     *
     * @param fieldStr 待转换的字符数据
     * @param fieldType 字段字符串
     * @return 解析后的字段
     */
    @Override
    public Map<?, ?> parseFiledStrToJavaClassType(String fieldStr, String fieldType) {
      Map<Object, Object> map = new LinkedHashMap<>(8);
      // 需要检查分隔符是否有重复的情况
      checkListMapFieldTypeDelimiter(fieldType);
      if (StringUtils.isEmpty(fieldStr)) {
        return getDefaultVal();
      }
      Matcher matcher = MAP_PATTERN.matcher(fieldType);
      if (matcher.find()) {

        String keySubType = matcher.group(1);
        String keyValDelimiterWithBracket = matcher.group(3);
        BracketMetadata keyDelimiter = getBracketInnerChar(keyValDelimiterWithBracket);

        String valType = matcher.group(5);
        String mapDelimiterWithBracket = matcher.group(6);
        BracketMetadata mapDelimiter = getBracketInnerChar(mapDelimiterWithBracket);

        ExcelFieldParseAdapter keyFieldDataAdapter = getFieldAdapterByTypeStr(keySubType);
        if (keyFieldDataAdapter.getFieldAdapter() instanceof BooleanFieldAdapter) {
          throw new ExcelFormatCheckException("解析类型错误Map类型字段的key值无法使用布尔类型");
        }
        if (!keyFieldDataAdapter.getFieldAdapter().isBaseType()) {
          List<String> basicTypeList = getBasicTypeList();
          String basicType = String.join(",", basicTypeList);
          throw new ExcelFormatCheckException(
              "Map类型解析错误,key不为基础类型(" + basicType + ") 当前类型: " + keySubType);
        }
        String replaceSplitDanglingMetaChar =
            replaceSplitDanglingMetaChar(mapDelimiter.delimiterChar);
        String[] mapArr = fieldStr.split(replaceSplitDanglingMetaChar);
        if (mapDelimiter.sizeLimit > 0 && mapArr.length > mapDelimiter.sizeLimit) {
          throw new ExcelDataParseException(
              "", "字段对应的数据数量: " + mapArr.length + " 超过限制值: " + mapDelimiter.sizeLimit);
        }
        for (String mapStr : mapArr) {
          if (StrUtils.isEmpty(mapStr)) {
            continue;
          }
          replaceSplitDanglingMetaChar = replaceSplitDanglingMetaChar(keyDelimiter.delimiterChar);
          String[] keyValArr = mapStr.split(replaceSplitDanglingMetaChar);
          // key原始值
          String keyStr = keyValArr[0];
          // key解析后的值
          Object keyParsed =
              keyFieldDataAdapter
                  .getFieldAdapter()
                  .parseFiledStrToJavaClassType(keyStr, keySubType);
          // val原始值
          String valStr = keyValArr[1];
          ExcelFieldParseAdapter valFieldDataAdapter = getFieldAdapterByTypeStr(valType);
          // val解析后的值
          Object valParsed =
              valFieldDataAdapter.getFieldAdapter().parseFiledStrToJavaClassType(valStr, valType);
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
      return new HashSet<>(Collections.singletonList(MAP_PATTERN.pattern()));
    }

    @Override
    public String getTargetFieldTypeStr(String fieldType) {
      Matcher matcher = MAP_PATTERN.matcher(fieldType);
      String mapTypeStr = "";
      if (matcher.find()) {

        // 需要检查分隔符是否有重复的情况
        checkListMapFieldTypeDelimiter(fieldType);

        String keySubType = matcher.group(1);
        String keyValDelimiterWithBracket = matcher.group(3);
        int originKeySubTypeLength = keySubType.length();

        String valTypeStr = matcher.group(5);
        String mapDelimiterWithBracket = matcher.group(6);
        int valTypeIdx = fieldType.lastIndexOf(valTypeStr);

        // 转换key类型
        ExcelFieldParseAdapter keyFieldDataAdapter = getFieldAdapterByTypeStr(keySubType);
        String keyType = keyFieldDataAdapter.getFieldAdapter().getTargetFieldObjTypeStr(keySubType);
        keySubType = replaceSplitDanglingMetaChar(keySubType);
        // 转换完成需要添加','
        mapTypeStr = fieldType.replaceFirst("[Mm]ap<" + keySubType, "Map<" + keyType + ",");

        // 转换val类型
        ExcelFieldParseAdapter valTypeFieldDataAdapter = getFieldAdapterByTypeStr(valTypeStr);
        String valType =
            valTypeFieldDataAdapter.getFieldAdapter().getTargetFieldObjTypeStr(valTypeStr);
        // 找到Val的位置，然后替换原始Val值，使用新的Val替换上去
        int newValTypeIdx = valTypeIdx - (originKeySubTypeLength - keyType.length());
        mapTypeStr =
            mapTypeStr.substring(0, newValTypeIdx + 1)
                + valType
                + mapTypeStr.substring(newValTypeIdx + valTypeStr.length() + 1);

        // 消除key-val之间以及map之间的分隔符
        mapTypeStr =
            mapTypeStr.replace(keyValDelimiterWithBracket, "").replace(mapDelimiterWithBracket, "");
      }
      return mapTypeStr;
    }

    @Override
    public boolean isBaseType() {
      return false;
    }
  }

  private static final Map<ExcelFieldParseAdapter, List<Pattern>> PATTERN_CACHE_MAP =
      new ConcurrentHashMap<>();

  /**
   * 查找类型字段
   *
   * @param typeStr 类型字符串
   * @return 目标类型字段
   */
  public static String getFieldTypeStr(String typeStr) {
    // 将检查字符串左右进行除空字符串处理
    ExcelFieldParseAdapter excelFieldTypeAdapter = getFieldAdapterByTypeStr(typeStr);
    return excelFieldTypeAdapter.getFieldAdapter().getTargetFieldTypeStr(typeStr);
  }

  /**
   * 查找类型字段
   *
   * @param typeStr 类型字符串
   * @return 目标类型字段
   */
  public static ExcelFieldParseAdapter getFieldAdapterByTypeStr(String typeStr) {
    // 将检查字符串左右进行除空字符串处理
    final String finalTypeStr = typeStr.trim().toLowerCase();
    List<ExcelFieldParseAdapter> parseAdapters = getFieldDataAdapterList();
    for (ExcelFieldParseAdapter value : parseAdapters) {
      if (!PATTERN_CACHE_MAP.containsKey(value)) {
        Set<String> matchStr = value.getFieldAdapter().getAcceptTypeStr();
        PATTERN_CACHE_MAP.computeIfAbsent(
            value, k -> matchStr.stream().map(Pattern::compile).collect(Collectors.toList()));
      }
      if (PATTERN_CACHE_MAP.get(value).stream()
          .anyMatch(
              pattern ->
                  pattern.pattern().equalsIgnoreCase(finalTypeStr)
                      || pattern.matcher(finalTypeStr).matches())) {
        return value;
      }
    }
    throw new ExcelFormatCheckException("字段类型: " + typeStr + " 找不到匹配项");
  }

  /**
   * 兼容枚举字段, 枚举字段是一个配置
   *
   * @return
   */
  private static List<ExcelFieldParseAdapter> getFieldDataAdapterList() {
    // 枚举类型需要放在最后处理，否则list和map套就无法嵌枚举类型
    return Arrays.stream(values())
        .sorted(
            (o1, o2) -> {
              if (o1 == ExcelFieldParseAdapter.ENUM) {
                return 1;
              }
              if (o2 == ExcelFieldParseAdapter.ENUM) {
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
    Matcher matcher = BRACKET_DELIMITER_PATTERN.matcher(fieldType);
    Set<String> delimiter = new HashSet<>();
    while (matcher.find()) {
      String group = matcher.group(1);
      if (!StrUtils.isEmpty(group)) {
        if (!delimiter.add(group)) {
          throw new ExcelFormatCheckException("列表或Map分隔符使用错误,出现重复的分隔符: " + group);
        }
      } else {
        throw new ExcelFormatCheckException("列表或Map分隔符使用错误,不能使用空格分隔符");
      }
    }
  }

  private static String filterFloatStrVal(String floatValStr) {
    String trimStr = floatValStr.trim();
    return StringUtils.isEmpty(trimStr)
        ? ""
        : (floatValStr.indexOf(".") > 0 ? trimStr.substring(0, floatValStr.indexOf(".")) : trimStr);
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
    // 括号内的分隔符
    String delimiterChar;
    // 长度限制大小
    int sizeLimit;
  }

  /** 获取括号内的字符 */
  private static BracketMetadata getBracketInnerChar(String strWithBracket) {
    Matcher mapDelimiterMatcher = BRACKET_DELIMITER_PATTERN.matcher(strWithBracket);
    BracketMetadata bracketMetadata = new BracketMetadata();
    if (mapDelimiterMatcher.find()) {
      bracketMetadata.delimiterChar = mapDelimiterMatcher.group(1);
      String sizeLimit = mapDelimiterMatcher.group(2);
      if (!StringUtils.isEmpty(sizeLimit) && DIGITAL_MATCH.matcher(sizeLimit).matches()) {
        bracketMetadata.sizeLimit = Integer.parseInt(sizeLimit);
      }
    }
    return bracketMetadata;
  }

  /**
   * 获取基础类型列表
   *
   * @return 类型列表str
   */
  private static List<String> getBasicTypeList() {
    List<String> basicTypeList = new ArrayList<>();
    for (ExcelFieldParseAdapter value : ExcelFieldParseAdapter.values()) {
      if (value.getFieldAdapter().isBaseType()) {
        basicTypeList.add(value.getFieldAdapter().getTargetFieldTypeStr(StrConstant.EMPTY));
      }
    }
    return basicTypeList;
  }
}
