package com.eouna.configtool.generator.template;

import com.eouna.configtool.configholder.ConfigDataBean.ExcelGenPathConf;
import com.eouna.configtool.configholder.ConfigDataBean.CommonTemplateConf;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.DataLoadSourceHolder;
import com.eouna.configtool.generator.MainEntryExportHolder;
import com.eouna.configtool.generator.base.ExcelFileStructure;
import com.eouna.configtool.generator.bean.ExcelSheetBean;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * 抽象模板生成器
 *
 * @author CCL
 * @date 2023/3/10
 */
public abstract class AbstractTemplateGenerator {

  /**
   * 生成之前
   *
   * @param excelFileStructureMap excel文件结构
   */
  public void generatorBefore(
      List<File> successGenList,
      Map<File, ExcelFileStructure> excelFileStructureMap,
      List<Exception> exceptions) {}

  /**
   * 生成一个excel文件
   *
   * @param file 目标文件
   * @param workbook excel实例
   * @param sheet 工作薄
   * @param sheetBean 工作薄基本信息
   * @param excelFileStructure excel文件结构
   * @throws Exception e
   */
  public abstract void generatorOneExcelFile(
      File file,
      Workbook workbook,
      Sheet sheet,
      ExcelSheetBean sheetBean,
      ExcelFileStructure excelFileStructure)
      throws Exception;

  /** 生成之后 */
  public void generatorAfter(
      TextAreaLogger textAreaLogger, Map<File, ExcelFileStructure> excelFileStructureMap) {}

  /** 获取模板所在子目录(默认java, 其他语言生成器可覆写) */
  protected abstract String getTemplateBindRelatedPath();

  /** 是否导出主函数入口代码 */
  protected boolean isExportMainEntry() {
    return MainEntryExportHolder.getInstance().isExportMainEntry();
  }

  /** 当前选择的数据读取方式是否为json */
  protected boolean isJsonLoadSource() {
    return DataLoadSourceHolder.getInstance().isJsonSource();
  }

  /**
   * 主入口默认读取目录。
   *
   * <p>Excel数据源指向原始Excel目录; JSON数据源指向本次模板导出的 {@code <导出目录>/json}, 方便主入口直接运行和预加载。
   */
  protected String getMainEntryDataPath() {
    ExcelGenPathConf pathConf = SystemConfigHolder.getInstance().getExcelConf().getPath();
    if (isJsonLoadSource()) {
      return pathConf.getTemplateFileGenTargetDir() + File.separator + "json";
    }
    return pathConf.getExcelConfigLoadPath();
  }

  /** 模板加载数据时是否打印数据明细(默认开启, 兼容缺少配置的旧配置文件) */
  protected boolean isPrintLoadData() {
    CommonTemplateConf templateConf = SystemConfigHolder.getInstance().getCommonTemplateConf();
    return templateConf == null || templateConf.isPrintLoadData();
  }

  /**
   * 按当前数据读取方式选择数据源模板名。
   *
   * <p>约定: 每个语言把"数据源读取"单独成类, 并准备两个模板 {@code 模板前缀 + Excel.ftl} / {@code 模板前缀 + Json.ftl},
   * 由界面选择的数据源决定用哪个。
   *
   * @param templatePrefix 模板前缀(例如 "DataSource")
   * @return 数据源模板文件名
   */
  protected String getDataSourceTemplateName(String templatePrefix) {
    return templatePrefix + (isJsonLoadSource() ? "Json.ftl" : "Excel.ftl");
  }

  /**
   * 获取freeMaker的配置解析器
   *
   * @return 返回
   * @throws IOException io
   */
  private Configuration getDefaultFreeMakerConfiguration() throws IOException {
    Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
    String templatePath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplatePath();
    String templateDir = templatePath + File.separator + getTemplateBindRelatedPath();
    configuration.setDirectoryForTemplateLoading(new File(templateDir));
    configuration.setDefaultEncoding("UTF-8");
    return configuration;
  }

  /**
   * 生成目标模板文件
   *
   * @param dataMap 数据map
   * @param templateName 模板名
   * @param outputFilePath 输出路径
   * @throws IOException e
   * @throws TemplateException e
   */
  protected void generateTemplate(
      Map<String, Object> dataMap, String templateName, String outputFilePath)
      throws IOException, TemplateException {
    try (OutputStreamWriter fileWriter =
        new OutputStreamWriter(
            Files.newOutputStream(Paths.get(outputFilePath)), StandardCharsets.UTF_8)) {
      // 获取模板文件
      Configuration configuration = getDefaultFreeMakerConfiguration();
      // 基本配置bean的模板文件
      Template template = configuration.getTemplate(templateName);
      template.process(dataMap, fileWriter);
      fileWriter.flush();
    }
  }
}
