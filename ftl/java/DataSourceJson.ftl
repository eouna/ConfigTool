package ${dataSourcePackageName};

// 本文件由配置表工具生成: 数据源读取实现(json)
//
// 读取工具导出的 <表名>.json, 并在内存中还原成与excel一致的工作薄视图,
// 这样容器里已有的解析/校验逻辑(list/set/map/date/enum...)可以完全复用。
//
// 依赖: gson(工具已内置, 生成工程需要引入 com.google.code.gson:gson)

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 配置数据源: 把"配置文件"打开成统一的工作薄视图。
 *
 * @author CCL
 */
public interface ConfigDataSource {

  /** 字段元数据(字段名 + 配置表中的原始类型), 按列顺序排列 */
  class FieldMeta {
    /** 字段名 */
    public final String name;
    /** 配置表中书写的原始类型 */
    public final String rawType;

    public FieldMeta(String name, String rawType) {
      this.name = name;
      this.rawType = rawType;
    }
  }

  Workbook openWorkbook(
      String rootPath,
      File file,
      String sheetName,
      List<FieldMeta> fields,
      int nameRow,
      int typeRow,
      int dataStartRow)
      throws Exception;

  String fileDigest(String rootPath, File file, String sheetName) throws Exception;

  boolean singleSource();

  /** 创建数据源(json实现) */
  static ConfigDataSource create() {
    return new JsonConfigDataSource();
  }
}

/** 基于gson + 内存工作薄的json数据源 */
class JsonConfigDataSource implements ConfigDataSource {

  /** 时间类型 date<yyyy-MM-dd HH:mm> */
  private static final Pattern DATE_TYPE = Pattern.compile("(?i)^date<(.*)>$");
  /** 列表 list<int>{,} / list<int>{,10} */
  private static final Pattern LIST_TYPE = Pattern.compile("(?i)^list<(.*)>\\{(.)(\\d*)\\}$");
  /** 集合 set<int>{,} */
  private static final Pattern SET_TYPE = Pattern.compile("(?i)^set<(.*)>\\{(.)(\\d*)\\}$");
  /** 键值对 map<int{,}String>{;} */
  private static final Pattern MAP_TYPE = Pattern.compile("(?i)^map<(.*)>\\{(.)(\\d*)\\}$");
  /** 键值对内部: 键类型{分隔符}值类型 */
  private static final Pattern MAP_INNER = Pattern.compile("^(.+?)\\{(.)\\}(.+)$");

  @Override
  public Workbook openWorkbook(
      String rootPath,
      File file,
      String sheetName,
      List<FieldMeta> fields,
      int nameRow,
      int typeRow,
      int dataStartRow)
      throws Exception {
    JsonObject rawData = readJson(rootPath, sheetName);
    XSSFWorkbook workbook = new XSSFWorkbook();
    Sheet sheet = workbook.createSheet(sheetName);
    int colCount = fields == null ? 0 : fields.size();
    int headRowCount = Math.max(dataStartRow, Math.max(nameRow, typeRow) + 1);
    for (int i = 0; i < headRowCount; i++) {
      sheet.createRow(i);
    }
    if (fields != null) {
      // 表头行: 类型行写原始类型, 其余表头行(描述行/数据范围行)写字段名,
      // 保证容器按"描述行列数"解析时能拿到完整列数
      for (int headRowIndex = 0; headRowIndex < headRowCount; headRowIndex++) {
        Row headRow = sheet.getRow(headRowIndex);
        if (headRow == null) {
          continue;
        }
        for (int col = 0; col < colCount; col++) {
          FieldMeta meta = fields.get(col);
          String headValue =
              headRowIndex == typeRow ? (meta.rawType == null ? "" : meta.rawType) : meta.name;
          headRow.createCell(col).setCellValue(headValue);
        }
      }
    }
    if (rawData == null || fields == null) {
      return workbook;
    }
    // 数据行按ID排序, 保证加载结果稳定
    Map<String, JsonElement> sorted = new TreeMap<>(JsonConfigDataSource::compareId);
    for (Map.Entry<String, JsonElement> entry : rawData.entrySet()) {
      if ("0".equals(entry.getKey().trim()) || !entry.getValue().isJsonObject()) {
        continue;
      }
      sorted.put(entry.getKey(), entry.getValue());
    }
    StringBuilder fieldNames = new StringBuilder();
    for (FieldMeta meta : fields) {
      fieldNames.append(meta.name).append(',');
    }
    System.out.println("[config-tool] json数据源: " + sheetName + " 条目数=" + sorted.size() + " 起始行=" + dataStartRow + " 字段=" + fieldNames);
    int rowIndex = dataStartRow;
    for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
      JsonObject item = entry.getValue().getAsJsonObject();
      Row row = sheet.createRow(rowIndex++);
      boolean hasValue = false;
      for (int col = 0; col < colCount; col++) {
        FieldMeta meta = fields.get(col);
        JsonElement value = item.get(meta.name);
        if (value == null || value.isJsonNull()) {
          continue;
        }
        row.createCell(col).setCellValue(valueToConfigString(meta.rawType, value));
        hasValue = true;
      }
      if (!hasValue) {
        sheet.removeRow(row);
        rowIndex--;
      }
    }
    return workbook;
  }

  @Override
  public String fileDigest(String rootPath, File file, String sheetName) throws Exception {
    File jsonFile = new File(rootPath, sheetName + ".json");
    try (InputStream inputStream = Files.newInputStream(jsonFile.toPath())) {
      return DigestUtils.md5Hex(inputStream);
    }
  }

  @Override
  public boolean singleSource() {
    return true;
  }

  /** 读取 <表名>.json */
  private JsonObject readJson(String rootPath, String sheetName) throws Exception {
    File jsonFile = new File(rootPath, sheetName + ".json");
    if (!jsonFile.isFile()) {
      throw new RuntimeException("找不到json数据文件: " + jsonFile.getAbsolutePath());
    }
    try (InputStreamReader reader =
        new InputStreamReader(Files.newInputStream(jsonFile.toPath()), StandardCharsets.UTF_8)) {
      JsonElement element = JsonParser.parseReader(reader);
      return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }
  }

  /** json值还原成配置表中的字符串写法 */
  private String valueToConfigString(String rawType, JsonElement value) {
    if (value == null || value.isJsonNull()) {
      return "";
    }
    String raw = rawType == null ? "" : rawType.trim();
    Matcher dateMatcher = DATE_TYPE.matcher(raw);
    if (dateMatcher.matches()) {
      return formatDate(dateMatcher.group(1), value.getAsString());
    }
    Matcher listMatcher = LIST_TYPE.matcher(raw);
    if (listMatcher.matches()) {
      return joinValues(listMatcher.group(1), listMatcher.group(2), value);
    }
    Matcher setMatcher = SET_TYPE.matcher(raw);
    if (setMatcher.matches()) {
      return joinValues(setMatcher.group(1), setMatcher.group(2), value);
    }
    Matcher mapMatcher = MAP_TYPE.matcher(raw);
    if (mapMatcher.matches()) {
      return joinMapValues(mapMatcher.group(1), mapMatcher.group(2), value);
    }
    return scalarToString(value);
  }

  /** 列表/集合: 元素用分隔符连接 */
  private String joinValues(String subType, String delimiter, JsonElement value) {
    if (!value.isJsonArray()) {
      return scalarToString(value);
    }
    List<String> parts = new ArrayList<>();
    for (JsonElement item : value.getAsJsonArray()) {
      parts.add(valueToConfigString(subType, item));
    }
    return String.join(delimiter, parts);
  }

  /** 键值对: "键,值;键,值" */
  private String joinMapValues(String innerType, String entryDelimiter, JsonElement value) {
    Matcher inner = MAP_INNER.matcher(innerType);
    if (!inner.matches() || !value.isJsonObject()) {
      return scalarToString(value);
    }
    String keyValueDelimiter = inner.group(2);
    String valueType = inner.group(3);
    Map<String, JsonElement> sorted = new TreeMap<>(JsonConfigDataSource::compareId);
    for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
      sorted.put(entry.getKey(), entry.getValue());
    }
    List<String> parts = new ArrayList<>();
    for (Map.Entry<String, JsonElement> entry : sorted.entrySet()) {
      parts.add(entry.getKey() + keyValueDelimiter + valueToConfigString(valueType, entry.getValue()));
    }
    return String.join(entryDelimiter, parts);
  }

  /** 基础类型/枚举转字符串 */
  private String scalarToString(JsonElement value) {
    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
      return trimNumber(value.getAsBigDecimal().stripTrailingZeros().toPlainString());
    }
    return value.getAsString();
  }

  private String trimNumber(String number) {
    return number.endsWith(".0") ? number.substring(0, number.length() - 2) : number;
  }

  /** 时间: 解析常见格式后按 type 中的格式输出 */
  private String formatDate(String javaPattern, String text) {
    String[] candidatePatterns = {
      "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm:ss",
      "yyyy-MM-dd HH:mm", "yyyy-MM-dd"
    };
    for (String candidate : candidatePatterns) {
      try {
        java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(candidate);
        java.util.Date parsed = parser.parse(text);
        if (parsed != null) {
          return new java.text.SimpleDateFormat(javaPattern).format(parsed);
        }
      } catch (java.text.ParseException ignored) {
        // 尝试下一个格式
      }
    }
    return text;
  }

  /** ID比较: 数字优先按数值比较 */
  private static int compareId(String left, String right) {
    try {
      return Long.compare(Long.parseLong(left), Long.parseLong(right));
    } catch (NumberFormatException ignored) {
      return left.compareTo(right);
    }
  }
}
