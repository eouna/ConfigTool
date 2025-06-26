package com.eouna.configtool.generator.template.java;

import com.eouna.configtool.configholder.ConfigDataBean.ExcelGenPathConf;
import com.eouna.configtool.configholder.ConfigDataBean.JavaTemplateConf;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.generator.base.ExcelFileStructure;
import com.eouna.configtool.generator.bean.ExcelDataStruct;
import com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelEnumFieldInfo;
import com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelFieldInfo;
import com.eouna.configtool.generator.bean.ExcelSheetBean;
import com.eouna.configtool.generator.exceptions.ExcelParseException;
import com.eouna.configtool.generator.template.AbstractTemplateGenerator;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.StrUtils;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * java模板生成器
 *
 * @author CCL
 * @date 2023/3/10
 */
public class JavaTemplateGenerator extends AbstractTemplateGenerator {

  /** 配置表和配置表容器map */
  protected Map<String, String> cfgBeanOfContainerNameMap =
      new ConcurrentSkipListMap<>(String::compareTo);

  /** bean名和container名对应的文件 */
  protected Map<String, ExcelSheetBean> cfgBeanAndContainerRecMap = new ConcurrentHashMap<>();

  @Override
  public void generatorBefore(
      List<File> successGenList, Map<File, ExcelFileStructure> excelFileStructureMap) {
    try {
      // 生成前清理一次 确保数据准确
      cfgBeanOfContainerNameMap.clear();
      cfgBeanAndContainerRecMap.clear();
      // 生成基础类bean和container
      generateBaseBeanAndContainer();
      // 生成之后的父节点列表 防止重复生成
      Set<String> generatedParentList = new HashSet<>();
      for (Map.Entry<File, ExcelFileStructure> fileSture : excelFileStructureMap.entrySet()) {
        File currentDealFile = fileSture.getKey();
        if (generatedParentList.contains(currentDealFile.getName())) {
          continue;
        }
        ExcelFileStructure fileStureValue = fileSture.getValue();
        // 父类的文件是虚拟的文件不存在
        if (!currentDealFile.exists()) {
          // 生成父节点文件 需要包含所有
          generateParentFile(successGenList, currentDealFile, fileStureValue);
          // 生成成功后的文件
          generatedParentList.add(currentDealFile.getName());
        }
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("生成父Java模板时发生异常", e);
    }
  }

  /** 生成基础bean和container */
  private void generateBaseBeanAndContainer() throws IOException, TemplateException {
    String basePath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
            + File.separator;

    // 生成bean模板
    File baseBeanOutputFileDir = new File(basePath + DefaultEnvConfigConstant.CFG_BEAN_PATH);
    if (!baseBeanOutputFileDir.exists()) {
      if (!baseBeanOutputFileDir.mkdir()) {
        throw new IOException("不能创建文件夹: " + baseBeanOutputFileDir);
      }
    }
    // 获取输路径
    String baseBeanOutputFilePath =
        baseBeanOutputFileDir.getPath()
            + File.separator
            + DefaultEnvConfigConstant.BASE_BEAN_TEMPLATE_CLASS_NAME
            + ETemplateGenerator.JAVA_GENERATOR.getTemplateHandler().getFileIdentifier();
    String cfgBeanPackageName = getCfgBeanPackageName();
    Map<String, Object> dataMap = new HashMap<>(1);
    dataMap.put("packageName", cfgBeanPackageName);
    dataMap.put(
        "idName", SystemConfigHolder.getInstance().getJavaTemplateConf().getBaseBeanIdName());
    // 生成模板文件
    generateTemplate(
        dataMap, DefaultEnvConfigConstant.BASE_CFG_BEAN_TEMPLATE_NAME, baseBeanOutputFilePath);

    // 生成bean容器模板
    File baseContainerOutputFileDir = new File(basePath + DefaultEnvConfigConstant.CONTAINER_PATH);
    if (!baseContainerOutputFileDir.exists()) {
      if (!baseContainerOutputFileDir.mkdir()) {
        throw new IOException("不能创建文件夹: " + baseBeanOutputFileDir);
      }
    }
    // 获取输路径
    String baseContainerOutputFilePath =
        baseContainerOutputFileDir.getPath()
            + File.separator
            + DefaultEnvConfigConstant.BASE_CONTAINER_TEMPLATE_CLASS_NAME
            + ETemplateGenerator.JAVA_GENERATOR.getTemplateHandler().getFileIdentifier();
    // 获取excel字段配置
    ExcelFieldInfo fieldInfo = new ExcelFieldInfo();
    String cfgBeanContainerPackageName = getCfgBeanContainerPackageName();
    dataMap = new HashMap<>(8);
    dataMap.put("packageName", cfgBeanContainerPackageName);
    dataMap.put("fieldInfo", fieldInfo);
    dataMap.put("dataStartRow", ExcelTemplateGenUtils.getConfigFieldMaxRow() + 1);
    dataMap.put("beanPackageName", cfgBeanPackageName);
    dataMap.put("baseCfgBean", DefaultEnvConfigConstant.BASE_BEAN_TEMPLATE_CLASS_NAME);
    dataMap.put(
        "idName",
        StrUtils.upperFirst(
            SystemConfigHolder.getInstance().getJavaTemplateConf().getBaseBeanIdName()));
    dataMap.put(
        "skipStr",
        SystemConfigHolder.getInstance().getJavaTemplateConf().getDataRangeServerSkipStr());
    generateTemplate(
        dataMap,
        DefaultEnvConfigConstant.BASE_CFG_BEAN_CONTAINER_TEMPLATE_NAME,
        baseContainerOutputFilePath);
  }

  /**
   * 生成父文件 1. 拿取子类的excel文件字段 2. 拼接合并字段到父类中 3. 生成父类文件
   *
   * @param successGenList 生成成功的文件
   * @param file 需要生成的文件
   */
  private void generateParentFile(
      List<File> successGenList, File file, ExcelFileStructure fileSture) throws Exception {
    ExcelSheetBean sheetBean = new ExcelSheetBean(file, file.getName());
    // 生成一个文件
    generatorOneExcelFile(file, null, null, sheetBean, fileSture);
    // 添加生成成功文件
    successGenList.add(file);
  }

  @Override
  public void generatorOneExcelFile(
      File file,
      Workbook workbook,
      Sheet sheet,
      ExcelSheetBean sheetBean,
      ExcelFileStructure excelFileStructure)
      throws Exception {
    // 先检查文件是否已经存在
    checkRepeatGenFile(sheetBean);
    // 生成配置表bean模板
    generateOneCfgBean(file, sheet, sheetBean, excelFileStructure);
    // 生成配置表bean容器模板
    generateOneCfgContainerBean(sheetBean, excelFileStructure);
  }

  /**
   * 生成配置表cfgBean
   *
   * @param sheet 工作薄
   * @param sheetBean 工作薄解析后的bean
   * @throws Exception e.
   */
  private void generateOneCfgBean(
      File curGenFile, Sheet sheet, ExcelSheetBean sheetBean, ExcelFileStructure excelFileStructure)
      throws Exception {
    // 配置bean的class名
    String cfgBeanClassName = getCfgBeanClassName(sheetBean.getSheetName());
    // 是否是父类
    boolean isParent = excelFileStructure.getParentFile() == curGenFile;
    // 子excel
    Collection<File> childExcel =
        isParent ? excelFileStructure.getChildFileSet() : Collections.singletonList(curGenFile);
    // 配置父级的class名
    String parentClassName =
        isParent
            ? DefaultEnvConfigConstant.BASE_BEAN_TEMPLATE_CLASS_NAME
            : getCfgBeanClassName(excelFileStructure.getParentFile().getName());
    // 获取输出路径
    String outputFilePath = getBeanCfgPath(sheetBean);

    // 加载字段信息
    ExcelDataStruct dataStruct = loadFieldInfo(!isParent, curGenFile, childExcel, sheet, sheetBean);

    Map<String, Object> dataMap = new HashMap<>(8);
    String packageName = getCfgBeanPackageName();
    dataMap.put("packageName", packageName);
    dataMap.put("dataStruct", dataStruct);
    dataMap.put("beanClassName", cfgBeanClassName);
    dataMap.put("parentClass", parentClassName);
    dataMap.put("date", getGenerateDate());
    // 调用生成逻辑
    generateTemplate(dataMap, DefaultEnvConfigConstant.CFG_BEAN_TEMPLATE_NAME, outputFilePath);
    // 保存生成成功之后的路径和工作薄数据
    cfgBeanAndContainerRecMap.put(outputFilePath, sheetBean);
  }

  /**
   * 获取生成时间
   *
   * @return 时间字符串
   */
  private String getGenerateDate() {
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");
    return simpleDateFormat.format(new Date());
  }

  /**
   * 获取配置bean的class名
   *
   * @param sheetName 工作薄名
   * @return class名
   */
  private String getCfgBeanClassName(String sheetName) {
    String cfgBeanClassSuffix = "cfg";
    String cfgBeanClassName = sheetName;
    if (!cfgBeanClassName.endsWith(StrUtils.upperFirst(cfgBeanClassSuffix))) {
      // cfgClass名替换名字为以Cfg为结尾
      cfgBeanClassName =
          cfgBeanClassName.endsWith(cfgBeanClassSuffix)
              ? cfgBeanClassName.substring(cfgBeanClassName.lastIndexOf(cfgBeanClassSuffix))
                  + StrUtils.upperFirst(cfgBeanClassSuffix)
              : cfgBeanClassName + StrUtils.upperFirst(cfgBeanClassSuffix);
    }
    return StrUtils.upperFirst(cfgBeanClassName);
  }

  /**
   * 加载字段信息
   *
   * @param hasParentSheet 是否是有父表,用于控制是否生成当前表中的枚举
   * @param sheet 表信息
   * @param sheetBean 表bean
   * @return 字段信息
   */
  private ExcelDataStruct loadFieldInfo(
      boolean hasParentSheet,
      File file,
      Collection<File> childExcelFileList,
      Sheet sheet,
      ExcelSheetBean sheetBean)
      throws Exception {
    // excel字段数据结构
    ExcelDataStruct dataStruct = new ExcelDataStruct(file.getName(), sheetBean.getSheetName());
    Set<String> sameFieldNameFilter = new HashSet<>();
    // 移除子类id字段
    sameFieldNameFilter.add(
        SystemConfigHolder.getInstance().getJavaTemplateConf().getBaseBeanIdName());
    String skipStr =
        SystemConfigHolder.getInstance().getJavaTemplateConf().getDataRangeServerSkipStr();
    Set<Integer> skipColList = ExcelTemplateGenUtils.getSkipCellList(sheet, skipStr);
    if (childExcelFileList.size() > 1) {
      // 加载子excel信息
      for (File childExcelFile : childExcelFileList) {
        Workbook workbook = null;
        try {
          workbook = WorkbookFactory.create(childExcelFile, null, true);
          Sheet childSheet = workbook.getSheetAt(0);
          // 获取excel字段信息
          Set<ExcelFieldInfo> excelFieldInfo =
              ExcelTemplateGenUtils.getExcelFields(file, childSheet, skipColList);
          // 移除同名字段
          excelFieldInfo.removeIf(
              fieldInfo -> sameFieldNameFilter.contains(fieldInfo.getFieldName().getFieldData()));
          // 填充excel字段信息
          fillDataStructByExcelFieldInfo(hasParentSheet, dataStruct, excelFieldInfo);
          // 处理同名字段
          sameFieldNameFilter.addAll(
              excelFieldInfo.stream()
                  .map(fieldInfo -> fieldInfo.getFieldName().getFieldData())
                  .collect(Collectors.toList()));
        } finally {
          if (workbook != null) {
            try {
              workbook.close();
            } catch (IOException e) {
              LoggerUtils.getLogger().error("关闭excel:" + file.getName() + "工作薄失败", e);
            }
          }
        }
      }
      // 由于父节点的字段由多个文件组成所以需在外层进行单独排序 对枚举字段进行排序
      List<ExcelEnumFieldInfo> excelEnumFieldInfos =
          new ArrayList<>(dataStruct.getExcelEnumFieldInfoList());
      excelEnumFieldInfos.sort(Comparator.comparing(o -> o.getFieldName().getFieldData()));
      dataStruct.getExcelEnumFieldInfoList().clear();
      dataStruct.getExcelEnumFieldInfoList().addAll(excelEnumFieldInfos);
      // 对普通字段进行排序
      List<ExcelFieldInfo> excelFieldInfos = new ArrayList<>(dataStruct.getExcelFieldInfoList());
      excelFieldInfos.sort(Comparator.comparing(o -> o.getFieldName().getFieldData()));
      dataStruct.getExcelFieldInfoList().clear();
      dataStruct.getExcelFieldInfoList().addAll(excelFieldInfos);
    } else {
      if (sheet != null) {
        // 获取excel字段信息
        Set<ExcelFieldInfo> excelFieldInfo =
            ExcelTemplateGenUtils.getExcelFields(file, sheet, skipColList);
        // 移除同名字段
        excelFieldInfo.removeIf(
            fieldInfo -> sameFieldNameFilter.contains(fieldInfo.getFieldName().getFieldData()));
        // 填充excel字段信息
        fillDataStructByExcelFieldInfo(hasParentSheet, dataStruct, excelFieldInfo);
      }
    }
    return dataStruct;
  }

  /**
   * 填充excel数据字段
   *
   * @param dataStruct 数据结构
   * @param excelFieldInfo excel字段信息
   */
  private void fillDataStructByExcelFieldInfo(
      boolean hasParentSheet, ExcelDataStruct dataStruct, Set<ExcelFieldInfo> excelFieldInfo) {
    Set<ExcelEnumFieldInfo> excelEnumFieldInfoList = new HashSet<>();
    for (ExcelFieldInfo fieldInfo : excelFieldInfo) {
      if (!hasParentSheet && (fieldInfo instanceof ExcelEnumFieldInfo)) {
        excelEnumFieldInfoList.add((ExcelEnumFieldInfo) fieldInfo);
      }
      dataStruct.getExcelFieldInfoList().add(fieldInfo);
    }
    if (!excelEnumFieldInfoList.isEmpty()) {
      // 枚举字段也需要进行排序
      dataStruct
          .getExcelEnumFieldInfoList()
          .addAll(
              excelEnumFieldInfoList.stream()
                  .sorted(Comparator.comparing(o -> o.getFieldName().getFieldData()))
                  .collect(Collectors.toList()));
    }
  }

  /** 生成配置表容器bean */
  private void generateOneCfgContainerBean(
      ExcelSheetBean sheetBean, ExcelFileStructure excelFileStructure)
      throws IOException, TemplateException {
    // 当前文件
    File curGenFile = excelFileStructure.getCurrent();
    // 配置bean的class名
    String cfgBeanClassName = getCfgBeanClassName(sheetBean.getSheetName());
    // 配置bean容器的class名
    String cfgBeanContainerClassName =
        cfgBeanClassName + DefaultEnvConfigConstant.CONTAINER_GEN_SUFFIX;
    // 添加映射数据
    cfgBeanOfContainerNameMap.put(cfgBeanClassName, cfgBeanContainerClassName);
    // 是否是父类
    boolean isChild = excelFileStructure.getParentFile() == excelFileStructure.getCurrent();
    // 子excel
    Collection<File> childExcel =
        isChild ? Collections.singletonList(curGenFile) : excelFileStructure.getChildFileSet();
    // 配置父级的class名
    String parentClassName = DefaultEnvConfigConstant.BASE_CONTAINER_TEMPLATE_CLASS_NAME;
    String containerBasePath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
            + File.separator
            + DefaultEnvConfigConstant.CONTAINER_PATH;
    File basePathFile = new File(containerBasePath);
    if (!(basePathFile.exists())) {
      if (!basePathFile.mkdir()) {
        throw new FileSystemException("文件夹创建失败 path: " + containerBasePath);
      }
    }
    // 获取输路径
    String outputFilePath = getBeanCfgContainerPath(sheetBean);

    Map<String, Object> dataMap = new HashMap<>(8);
    String containerPackageName = getCfgBeanContainerPackageName();
    String beanPackageName = getCfgBeanPackageName();
    // excel加载路径
    String excelConfigLoadPath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath();
    // 是否保持绑定的excel相对路径
    boolean isKeepBindExcelRelativePath =
        SystemConfigHolder.getInstance().getJavaTemplateConf().isKeepBindExcelRelativePath();
    List<String> bindExcelList =
        childExcel.stream()
            .map(
                file -> {
                  if (isKeepBindExcelRelativePath) {
                    return file.getAbsolutePath()
                        .replace(excelConfigLoadPath + File.separator, "")
                        .replace(File.separator, "\\" + File.separator);
                  }
                  return file.getName();
                })
            .sorted(String::compareTo)
            .collect(Collectors.toList());
    dataMap.put("containerPackageName", containerPackageName);
    dataMap.put("beanPackageName", beanPackageName);
    dataMap.put("bindExcelList", bindExcelList);
    // 是否具有关联表, 有分表的子表和父表都应为true
    dataMap.put("hasRelatedTable", childExcel.size() > 1 || !isChild);
    // 是否是父节点
    dataMap.put("isParentNode", childExcel.size() > 1);
    dataMap.put("containerClassName", cfgBeanContainerClassName);
    dataMap.put("beanClassName", cfgBeanClassName);
    dataMap.put("parentClassName", parentClassName);
    dataMap.put("sheetBean", sheetBean);
    dataMap.put("excelName", curGenFile.getName());
    dataMap.put("date", getGenerateDate());
    // 生成模板文件
    generateTemplate(dataMap, DefaultEnvConfigConstant.CFG_CONTAINER_TEMPLATE_NAME, outputFilePath);
    // 保存生成成功之后的路径和工作薄数据
    cfgBeanAndContainerRecMap.put(outputFilePath, sheetBean);
  }

  /**
   * 检查是否有重复的生成文件 如果有同名的工作薄名会出现此问题
   *
   * @param sheetBean sheet数据
   * @throws FileSystemException e
   */
  private void checkRepeatGenFile(ExcelSheetBean sheetBean) throws FileSystemException {
    // 获取输路径
    String outputFilePath = getBeanCfgPath(sheetBean);
    File beanFile = new File(outputFilePath);
    // 如果输出的文件路径存在则说明已经生成过一次
    if (beanFile.exists()) {
      ExcelSheetBean excelSheetBean = cfgBeanAndContainerRecMap.get(outputFilePath);
      throw new ExcelParseException(
          "生成Bean文件失败,文件已存在! Excel文件名: "
              + sheetBean.getFile().getName()
              + " 工作薄名: "
              + sheetBean.getSheetName()
              + " 重复文件: "
              + beanFile.getName()
              + " 已存在的bean由文件名: "
              + excelSheetBean.getFile().getName()
              + " 生成");
    }
    // 获取输路径
    outputFilePath = getBeanCfgContainerPath(sheetBean);
    File containerFile = new File(outputFilePath);
    // 如果输出的文件路径存在则说明已经生成过一次
    if (containerFile.exists()) {
      ExcelSheetBean excelSheetBean = cfgBeanAndContainerRecMap.get(outputFilePath);
      throw new ExcelParseException(
          "生成Container文件失败,文件已存在! Excel文件名: "
              + sheetBean.getFile().getName()
              + " 工作薄名: "
              + sheetBean.getSheetName()
              + " 重复文件: "
              + beanFile.getName()
              + " 已存在的bean由文件名: "
              + excelSheetBean.getFile().getName()
              + " 生成");
    }
  }

  /**
   * 通过sheet获取beanCfg生成路径
   *
   * @param sheetBean sheet数据
   * @return 生成路径
   * @throws FileSystemException e
   */
  private String getBeanCfgPath(ExcelSheetBean sheetBean) throws FileSystemException {
    // 配置bean的class名
    String cfgBeanClassName = getCfgBeanClassName(sheetBean.getSheetName());
    String basePath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
            + File.separator
            + DefaultEnvConfigConstant.CFG_BEAN_PATH;
    // 获取或者创建文件路径
    FileUtils.getOrCreateDir(basePath);
    // 获取输路径
    String outputFilePath =
        basePath
            + File.separator
            + cfgBeanClassName
            + ETemplateGenerator.JAVA_GENERATOR.getTemplateHandler().getFileIdentifier();
    return outputFilePath;
  }

  /**
   * 通过sheet获取beanCfgContainer生成路径
   *
   * @param sheetBean sheet数据
   * @return 生成路径
   * @throws FileSystemException e
   */
  private String getBeanCfgContainerPath(ExcelSheetBean sheetBean) throws FileSystemException {
    // 配置bean的class名
    String cfgBeanClassName = getCfgBeanClassName(sheetBean.getSheetName());
    // 配置bean容器的class名
    String cfgBeanContainerClassName =
        cfgBeanClassName + DefaultEnvConfigConstant.CONTAINER_GEN_SUFFIX;

    String containerBasePath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
            + File.separator
            + DefaultEnvConfigConstant.CONTAINER_PATH;
    // 获取或者创建文件路径
    FileUtils.getOrCreateDir(containerBasePath);
    // 获取输路径
    String outputFilePath =
        containerBasePath
            + File.separator
            + cfgBeanContainerClassName
            + ETemplateGenerator.JAVA_GENERATOR.getTemplateHandler().getFileIdentifier();

    return outputFilePath;
  }

  @Override
  public void generatorAfter(
      TextAreaLogger textAreaLogger, Map<File, ExcelFileStructure> excelFileStructureMap) {
    try {
      ExcelGenPathConf pathConf = SystemConfigHolder.getInstance().getExcelConf().getPath();
      // 生成GameDataManager
      String templateGenTargetDir = pathConf.getTemplateFileGenTargetDir() + File.separator;
      // 生成GameDataManager
      String excelLoadDir = pathConf.getExcelConfigLoadPath().replace("\\", "\\\\");
      // java模板配置
      JavaTemplateConf javaTemplateConf = SystemConfigHolder.getInstance().getJavaTemplateConf();
      // 获取输路径
      String outputFilePath =
          templateGenTargetDir
              + File.separator
              + javaTemplateConf.getDataManagerClassName()
              + ETemplateGenerator.JAVA_GENERATOR.getTemplateHandler().getFileIdentifier();

      String cfgBeanPackageName = getCfgBeanPackageName();
      String containerPackageName = getCfgBeanContainerPackageName();

      Map<String, Object> dataMap = new HashMap<>(8);
      dataMap.put("beanAndContainerMap", cfgBeanOfContainerNameMap);
      dataMap.put("packageName", javaTemplateConf.getPackageName());
      dataMap.put("cfgBeanPackageName", cfgBeanPackageName);
      dataMap.put("containerPackageName", containerPackageName);
      dataMap.put("date", getGenerateDate());
      dataMap.put("dataManagerClassName", javaTemplateConf.getDataManagerClassName());
      dataMap.put("loadMethodName", javaTemplateConf.getDataManagerLoadDataCaller());
      dataMap.put("excelLoadDir", excelLoadDir);

      generateTemplate(
          dataMap, javaTemplateConf.getDataManagerClassName() + ".ftl", outputFilePath);

      textAreaLogger.info("生成JAVA模板文件结束");
    } catch (TemplateException | IOException e) {
      LoggerUtils.getLogger().error("生成GameDataManager时发生异常", e);
    } finally {
      // 结束后清空
      cfgBeanAndContainerRecMap.clear();
      cfgBeanOfContainerNameMap.clear();
    }
  }

  /** 配置表包名 */
  private String getCfgBeanPackageName() {
    return SystemConfigHolder.getInstance().getJavaTemplateConf().getPackageName()
        + "."
        + DefaultEnvConfigConstant.CFG_BEAN_PATH;
  }

  /** 配置表容器包名 */
  private String getCfgBeanContainerPackageName() {
    return SystemConfigHolder.getInstance().getJavaTemplateConf().getPackageName()
        + "."
        + DefaultEnvConfigConstant.CONTAINER_PATH;
  }

  /**
   * 单例
   *
   * @return JavaTemplateGenerator
   */
  public static JavaTemplateGenerator getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final JavaTemplateGenerator instance;

    Singleton() {
      this.instance = new JavaTemplateGenerator();
    }

    public JavaTemplateGenerator getInstance() {
      return instance;
    }
  }
}
