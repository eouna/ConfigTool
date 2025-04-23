package com.eouna.configtool.generator.template;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.base.ExcelFileStructure;
import com.eouna.configtool.generator.bean.ExcelSheetBean;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
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
      List<File> successGenList, Map<File, ExcelFileStructure> excelFileStructureMap) {}

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

  /**
   * 获取freeMaker的配置解析器
   *
   * @return 返回
   * @throws IOException io
   */
  private Configuration getDefaultFreeMakerConfiguration() throws IOException {
    Configuration configuration = new Configuration(Configuration.VERSION_2_3_31);
    String templatePath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplatePath();
    String templateDir =
        templatePath
            + File.separator
            + ETemplateGenerator.JAVA_GENERATOR.getTemplateHandler().getTemplateBindRelatedPath();
    configuration.setDirectoryForTemplateLoading(new File(templateDir));
    configuration.setEncoding(Locale.SIMPLIFIED_CHINESE, "UTF-8");
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
            Files.newOutputStream(Paths.get(outputFilePath)), Charset.defaultCharset())) {
      // 获取模板文件
      Configuration configuration = getDefaultFreeMakerConfiguration();
      // 基本配置bean的模板文件
      Template template = configuration.getTemplate(templateName);
      template.process(dataMap, fileWriter);
      fileWriter.flush();
    }
  }
}
