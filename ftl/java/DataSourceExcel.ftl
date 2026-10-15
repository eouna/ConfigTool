package ${dataSourcePackageName};

// 本文件由配置表工具生成: 数据源读取实现(excel)
//
// 约定: 每种语言把"数据源读取"独立成一个类, 并通过两个模板提供两种实现
//   DataSourceExcel.ftl -> 读取excel
//   DataSourceJson.ftl  -> 读取json
// 界面「数据加载选项」/命令行 --load_source 选择哪个, 生成时就使用哪个模板。

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * 配置数据源: 负责把"配置文件"打开成统一的工作薄视图, 后续解析由容器统一处理。
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

  /**
   * 打开数据源工作薄
   *
   * @param rootPath 数据根目录
   * @param file 绑定的excel文件(json数据源按sheetName读取)
   * @param sheetName 表名(工作薄名)
   * @param fields 字段元数据(json数据源据此还原表头)
   * @param nameRow 字段名行
   * @param typeRow 字段类型行
   * @param dataStartRow 数据起始行
   * @return 工作薄
   */
  Workbook openWorkbook(
      String rootPath,
      File file,
      String sheetName,
      List<FieldMeta> fields,
      int nameRow,
      int typeRow,
      int dataStartRow)
      throws Exception;

  /** 文件摘要(用于热更新判断) */
  String fileDigest(String rootPath, File file, String sheetName) throws Exception;

  /** 是否"一张表只对应一个数据文件"(json为true, excel分表时为false) */
  boolean singleSource();

  /** 创建数据源(excel实现) */
  static ConfigDataSource create() {
    return new ExcelConfigDataSource();
  }
}

/** 基于POI的excel数据源 */
class ExcelConfigDataSource implements ConfigDataSource {

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
    return WorkbookFactory.create(file, null, true);
  }

  @Override
  public String fileDigest(String rootPath, File file, String sheetName) throws Exception {
    try (InputStream inputStream = Files.newInputStream(file.toPath())) {
      return DigestUtils.md5Hex(inputStream);
    }
  }

  @Override
  public boolean singleSource() {
    return false;
  }
}