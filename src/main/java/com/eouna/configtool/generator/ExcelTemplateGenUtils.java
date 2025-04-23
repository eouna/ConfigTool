package com.eouna.configtool.generator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.eouna.configtool.configholder.ConfigDataBean;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelEnumFieldInfo;
import com.eouna.configtool.generator.exceptions.BaseExcelException;
import com.eouna.configtool.generator.exceptions.ExcelFormatCheckException;
import com.eouna.configtool.generator.exceptions.ExcelParseException;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.generator.template.ExcelFieldParseAdapter;
import com.eouna.configtool.generator.template.ExcelFieldParseAdapter.EnumFieldAdapter;
import com.eouna.configtool.generator.template.IFieldAdapter;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.ui.controllers.ExcelGenWindowController;
import com.eouna.configtool.utils.ToolsLoggerUtils;
import com.eouna.configtool.utils.ExcelUtils;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.generator.base.ExcelFileStructure;
import com.eouna.configtool.generator.bean.ExcelDataStruct.ExcelFieldInfo;
import com.eouna.configtool.generator.bean.ExcelDataStruct.FieldMetadata;
import com.eouna.configtool.generator.bean.ExcelSheetBean;
import com.eouna.configtool.utils.StrUtils;
import freemarker.template.TemplateException;
import javafx.application.Platform;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * excel转模板文件
 *
 * @author CCL
 * @date 2023/3/2
 */
public class ExcelTemplateGenUtils {

  /** 文件名检测正则 */
  public static final Pattern FILE_NAME_PATTERN =
      Pattern.compile(
          "^[a-zA-Z]+"
              + DefaultEnvConfigConstant.EXCEL_STRUCTURE_DELIMITER
              + "{0,"
              + DefaultEnvConfigConstant.EXCEL_STRUCTURE_MAX_DEPTH
              + "}[a-zA-Z]+\\.(xlsx|xls)");

  /** 普通异常收集 */
  public static final List<Exception> NORMAL_EXCEPTION_COLLECTOR = new ArrayList<>();

  /** 并行异常收集 */
  public static final List<Exception> EXCEPTION_COLLECTOR = new CopyOnWriteArrayList<>();

  /** excel生成模板的线程工厂 */
  private static final ThreadPoolExecutor EXCEL_GEN_EXECUTOR;

  /** 并行模板生成锁 */
  private static final AtomicBoolean PARALLEL_GEN_LOCKER = new AtomicBoolean(false);

  static {
    int availableProcess = Runtime.getRuntime().availableProcessors();
    int processNum = Math.max(availableProcess / 2 + 1, 4);
    // 单个excel文件预计生成时间不会超过一秒钟时间
    EXCEL_GEN_EXECUTOR =
        new ThreadPoolExecutor(
            processNum,
            processNum,
            1,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingDeque<>(100),
            new DefaultTemplateGenThreadFactory(),
            new CallerRunsPolicy());
    EXCEL_GEN_EXECUTOR.allowCoreThreadTimeOut(true);
  }

  /**
   * excel文件列表转模板对应的文件
   *
   * @param excelFileList excel文件列表
   */
  public static void generateByTemplate(
      TextAreaLogger textAreaLogger,
      Collection<File> excelFileList,
      Set<ETemplateGenerator> templateGenerators,
      Consumer<Boolean> finishedCallBack) {
    // 先清除一次异常收集器
    NORMAL_EXCEPTION_COLLECTOR.clear();
    long startTime = System.currentTimeMillis();
    List<File> successGenList = new ArrayList<>();
    // 生成模板之前
    beforeTemplateGenerate();
    // 加载文件结构
    Map<File, ExcelFileStructure> excelFileStructure = buildExcelFileStructure(excelFileList);
    // 调用模板生成之前的逻辑
    templateGenerators.forEach(
        templateGenerator ->
            templateGenerator
                .getTemplateGenerator()
                .generatorBefore(successGenList, excelFileStructure));
    for (Map.Entry<File, ExcelFileStructure> fileSture : excelFileStructure.entrySet()) {
      File currentDealFile = fileSture.getKey();
      if (!currentDealFile.exists()) {
        LoggerUtils.getLogger().info("跳过父节点: " + currentDealFile.getName());
        continue;
      }
      // 生成一个模板
      generateOneExcelByTemplate(
          textAreaLogger,
          fileSture.getKey(),
          templateGenerators,
          successGenList,
          fileSture.getValue(),
          NORMAL_EXCEPTION_COLLECTOR);
    }
    // 完成后调用
    whenGenTemplateFinished(
        textAreaLogger,
        templateGenerators,
        finishedCallBack,
        excelFileStructure,
        startTime,
        successGenList,
        NORMAL_EXCEPTION_COLLECTOR);
  }

  /**
   * excel文件列表转模板对应的文件
   *
   * @param excelFileList excel文件列表
   */
  public static void generateByTemplateParallel(
      TextAreaLogger textAreaLogger,
      Collection<File> excelFileList,
      Set<ETemplateGenerator> templateGenerators) {
    generateByTemplateParallel(textAreaLogger, excelFileList, templateGenerators, (res) -> {});
  }

  /**
   * excel文件列表转模板对应的文件 并行运行使用默认线程池
   *
   * @param excelFileList excel文件列表
   * @param finishedCallBack 完成后的回调
   * @param templateGenerators 需要生成的模板列表
   */
  public static void generateByTemplateParallel(
      TextAreaLogger textAreaLogger,
      Collection<File> excelFileList,
      Set<ETemplateGenerator> templateGenerators,
      Consumer<Boolean> finishedCallBack) {
    generateByTemplateParallel(
        textAreaLogger, excelFileList, templateGenerators, finishedCallBack, EXCEL_GEN_EXECUTOR);
  }

  /**
   * excel文件列表转模板对应的文件 并行运行
   *
   * @param excelFileList excel文件列表
   * @param finishedCallBack 完成后的回调
   * @param templateGenerators 模板生成器列表
   * @param excelGenExecutorPool excel生成使用的线程池
   */
  public static void generateByTemplateParallel(
      TextAreaLogger textAreaLogger,
      Collection<File> excelFileList,
      Set<ETemplateGenerator> templateGenerators,
      Consumer<Boolean> finishedCallBack,
      ThreadPoolExecutor excelGenExecutorPool) {
    synchronized (PARALLEL_GEN_LOCKER) {
      if (!PARALLEL_GEN_LOCKER.compareAndSet(false, true)) {
        textAreaLogger.error("当前已有模板生成任务正在进行中...");
        return;
      }
    }
    // 先清除一次异常收集器
    EXCEPTION_COLLECTOR.clear();
    long startTime = System.currentTimeMillis();
    List<File> successGenList = new CopyOnWriteArrayList<>();
    // 调用模板生成前逻辑
    beforeTemplateGenerate();
    // 构建文件结构
    Map<File, ExcelFileStructure> excelFileStructureMap = buildExcelFileStructure(excelFileList);
    // 模板生成计数器
    AtomicInteger generatorCounter =
        new AtomicInteger(
            (int) excelFileStructureMap.keySet().stream().filter(File::exists).count());
    // 调用模板生成之前的逻辑
    templateGenerators.forEach(
        templateGenerator ->
            templateGenerator
                .getTemplateGenerator()
                .generatorBefore(successGenList, excelFileStructureMap));
    for (Map.Entry<File, ExcelFileStructure> fileSture : excelFileStructureMap.entrySet()) {
      File currentDealFile = fileSture.getKey();
      ExcelFileStructure fileStureValue = fileSture.getValue();
      if (!currentDealFile.exists()) {
        LoggerUtils.getLogger().info("跳过父节点: " + currentDealFile.getName());
        continue;
      }
      // 将excel抛入excel生成专用线程处理
      excelGenExecutorPool.execute(
          () -> {
            try {
              generateOneExcelByTemplate(
                  textAreaLogger,
                  currentDealFile,
                  templateGenerators,
                  successGenList,
                  fileStureValue,
                  EXCEPTION_COLLECTOR);
            } catch (Exception e) {
              LoggerUtils.getLogger()
                  .error(e.getMessage() + " trace: \n{}", ExceptionUtils.getStackTrace(e));
              textAreaLogger.info(
                  "生成配置表: {}, 异常: {}", currentDealFile.getName(), ExceptionUtils.getStackTrace(e));
              // 发生异常时是否立即退出
              if (DefaultEnvConfigConstant.IS_GEN_EXCEL_ERROR_EXIT_NOW) {
                generatorCounter.set(0);
              }
            } finally {
              generatorCounter.decrementAndGet();
            }
          });
    }
    DefaultFuture.runAsync(
            () -> {
              LoggerUtils.getLogger().info("等待模板生成完成...");
              // 空转等待生成完成
              while (generatorCounter.get() != 0) {}
              // 锁复位
              PARALLEL_GEN_LOCKER.set(false);
            })
        .whenComplete(
            (t, throwable) ->
                // 完成时调用
                whenGenTemplateFinished(
                    textAreaLogger,
                    templateGenerators,
                    finishedCallBack,
                    excelFileStructureMap,
                    startTime,
                    successGenList,
                    EXCEPTION_COLLECTOR));
  }

  /** 打印收集到的异常 */
  private static void formatExceptionMsg(List<Exception> exceptionCollector) {
    // 异常收集不为空
    if (!exceptionCollector.isEmpty()) {
      StringBuilder exceptionMsgBuilder = new StringBuilder();
      for (Exception exception : exceptionCollector) {
        if (exception instanceof BaseExcelException) {
          exceptionMsgBuilder =
              new StringBuilder(exception.getMessage() + "\n" + exceptionMsgBuilder);
        } else {
          exceptionMsgBuilder.append(ExceptionUtils.getStackTrace(exception)).append("\n");
        }
      }
      ToolsLoggerUtils.showErrorDialog("生成模板文件时发生异常", exceptionMsgBuilder.toString());
    }
  }

  /** 完成生成后调用 */
  private static void whenGenTemplateFinished(
      TextAreaLogger textAreaLogger,
      Set<ETemplateGenerator> templateGenerators,
      Consumer<Boolean> finishedCallBack,
      Map<File, ExcelFileStructure> excelFileStructureMap,
      long startTime,
      List<File> successGenList,
      List<Exception> exceptionCollector) {
    try {
      // 打印收集到的异常
      formatExceptionMsg(exceptionCollector);
      // 如果生成成功没有异常
      if (exceptionCollector.isEmpty()) {
        // 调用模板生成之后的逻辑
        templateGenerators.forEach(
            templateGenerator ->
                templateGenerator
                    .getTemplateGenerator()
                    .generatorAfter(textAreaLogger, excelFileStructureMap));
      }
      // 调用此方法的逻辑
      finishedCallBack.accept(exceptionCollector.isEmpty());
      textAreaLogger.info(
          "生成模板文件结束,总耗时: {}ms,生成文件数: {}",
          System.currentTimeMillis() - startTime,
          successGenList.size());
    } catch (Exception e) {
      ToolsLoggerUtils.showErrorDialog("调用生成成功回调异常", e);
    } finally {
      exceptionCollector.clear();
    }
  }

  /** 生成模板之前 */
  private static void beforeTemplateGenerate() {
    try {
      String genTargetDir =
          SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir();
      // 目标路径
      FileUtils.getOrCreateDir(genTargetDir);
      // 先删除模板文件夹中的文件
      org.apache.commons.io.FileUtils.cleanDirectory(new File(genTargetDir));
      // 创建bean目录
      String basePath = genTargetDir + File.separator + DefaultEnvConfigConstant.CFG_BEAN_PATH;
      // 获取或者创建文件路径
      FileUtils.getOrCreateDir(basePath);
      // 创建container目录
      String containerBasePath =
          genTargetDir + File.separator + DefaultEnvConfigConstant.CONTAINER_PATH;
      // 获取或者创建文件路径
      FileUtils.getOrCreateDir(containerBasePath);
    } catch (Exception e) {
      LoggerUtils.getLogger().error("清除文件错误", e);
    }
  }

  /** 构建文件关系, 实现文件的父子关系结构 文件以 _ 分隔 */
  public static Map<File, ExcelFileStructure> buildExcelFileStructure(
      Collection<File> excelFileList) {
    // 单个文件包含的父节点和子节点 双向绑定
    Map<File, ExcelFileStructure> excelFileStructure = new TreeMap<>();
    for (File file : excelFileList) {
      if (file == null || file.isDirectory()) {
        continue;
      }
      String fileName = file.getName();
      // 检查文件名是否正确
      Matcher matcher = FILE_NAME_PATTERN.matcher(fileName);
      if (!matcher.matches()) {
        throw new ExcelFormatCheckException(
            "构建文件结构时检查excel文件命名格式错误, 请以xxx.xlsx或xxx_xx.xlsx命名 文件名: " + fileName);
      }
      // 获取不带后缀的文件名
      String fileNameExcludeExtName = fileName.substring(0, fileName.lastIndexOf("."));
      String[] parentAndChildNames =
          fileNameExcludeExtName.split(DefaultEnvConfigConstant.EXCEL_STRUCTURE_DELIMITER);
      // TODO 待优化为支持多层级
      File parentFile = new File(parentAndChildNames[0]);
      // 有子表
      if (parentAndChildNames.length > 1) {
        // 添加一条节点数据, 节点数据为父类并添加一条子类数据
        File finalParentFile1 = parentFile;
        excelFileStructure
            .computeIfAbsent(
                parentFile, k -> new ExcelFileStructure(file, finalParentFile1, new HashSet<>()))
            .getChildFileSet()
            .add(file);
      } else {
        parentFile = file;
      }
      File finalParentFile2 = parentFile;
      // 子节点 设置父类文件和当前文件 如果当前没有父类则父类为自身且子节点也为自身
      excelFileStructure
          .computeIfAbsent(
              file, k -> new ExcelFileStructure(file, finalParentFile2, new HashSet<>()))
          .getChildFileSet()
          .add(file);
    }
    return excelFileStructure;
  }

  /**
   * 生成一个模板文件对应的文件
   *
   * @param file excel文件
   */
  public static void generateOneExcelByTemplate(
      TextAreaLogger textAreaLogger,
      File file,
      Set<ETemplateGenerator> templateGenerators,
      List<File> successGenList,
      ExcelFileStructure excelFileStructure,
      List<Exception> exceptionCollector) {
    ExcelGenWindowController controller =
        WindowManager.getInstance().getController(ExcelGenWindowController.class);
    Workbook workbook = null;
    try {
      // 获取excel工作簿
      workbook = WorkbookFactory.create(file, null, true);
      Sheet sheet = workbook.getSheetAt(0);
      ExcelSheetBean sheetBean = new ExcelSheetBean(file, sheet);
      int maxRow = getConfigFieldMaxRow();
      if (maxRow <= 0) {
        throw new ExcelParseException(
            "file: " + file.getName() + " valid row num is 0, check config");
      }
      // 遍历模板生成器进行处理
      for (ETemplateGenerator templateGenerator : templateGenerators) {
        templateGenerator
            .getTemplateGenerator()
            .generatorOneExcelFile(file, workbook, sheet, sheetBean, excelFileStructure);
      }
      // 添加生成成功文件
      successGenList.add(file);
      Platform.runLater(() -> controller.updateExcelProgress(file.getName(), true));
    } catch (Exception e) {
      if (e instanceof TemplateException) {
        LoggerUtils.getLogger().error("模板解析错误: " + file.getName(), e);
      } else if (e instanceof IOException) {
        textAreaLogger.error("资源查找错误,文件名: {}", e, file.getName());
      } else {
        textAreaLogger.error("解析文件 {} 时发生异常", e, file.getName());
      }
      Platform.runLater(() -> controller.updateExcelProgress(file.getName(), false));
      // 收集异常
      exceptionCollector.add(e);
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

  /**
   * 获取表中和字段相关的最大行数
   *
   * @return 行数
   */
  public static int getConfigFieldMaxRow() {
    List<Field> configKeyFieldList =
        FieldUtils.getAllFieldsList(ConfigDataBean.ExcelFieldConf.class);
    return configKeyFieldList.stream()
        .filter(field -> field.getName().startsWith("field") && field.getType() == int.class)
        .mapToInt(
            field -> {
              try {
                ConfigDataBean.ExcelFieldConf excelFieldConf =
                    SystemConfigHolder.getInstance().getExcelConf().getFieldRows();
                field.setAccessible(true);
                return (int) field.get(excelFieldConf);
              } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
              }
            })
        .boxed()
        .max(Integer::compare)
        .orElse(0);
  }

  /** 获取excel中所有的字段 */
  public static Set<ExcelFieldInfo> getExcelFields(
      File file, Sheet sheet, Set<Integer> skipColList) {
    // 字段信息
    Set<ExcelFieldInfo> excelFieldInfos = new HashSet<>();
    // sheetBean
    ExcelSheetBean sheetBean = new ExcelSheetBean(file, sheet);
    // 装载头部数据
    for (int colNum = 0; colNum <= sheetBean.getColEndNum(); colNum++) {
      try {
        ExcelFieldInfo excelFieldInfo = getExcelFieldInfo(sheet, colNum);
        if (excelFieldInfo == null) {
          continue;
        }
        // 需要跳过的列
        if (skipColList.contains(colNum)) {
          continue;
        }
        excelFieldInfos.add(excelFieldInfo);
      } catch (BaseExcelException excelException) {
        excelException.appendExceptionStr(
            "文件名: " + sheet.getSheetName() + " 列: " + (colNum + 1) + " 解析excel字段时发生异常");
        throw excelException;
      }
    }
    // 每次保证字段顺序一致
    excelFieldInfos =
        excelFieldInfos.stream()
            .sorted(Comparator.comparing(o -> o.getFieldName().getFieldData()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    return excelFieldInfos;
  }

  /** 获取列的字段的信息 */
  public static Map<Integer, ExcelFieldInfo> loadColFieldInfoMap(Sheet sheet, Row row) {
    Map<Integer, ExcelFieldInfo> excelFieldInfoMap = new HashMap<>(8);
    // 装载头部数据
    for (int colNum = 0; colNum <= row.getLastCellNum(); colNum++) {
      ExcelFieldInfo excelFieldInfo = new ExcelFieldInfo();
      // 加载字段类型
      loadFieldInfoData(sheet, colNum, excelFieldInfo.getFieldType());
      if (StringUtils.isEmpty(excelFieldInfo.getFieldType().getFieldData())) {
        continue;
      } else {
        String fieldTypeStr = excelFieldInfo.getFieldType().getFieldData();
        ExcelFieldParseAdapter excelFieldTypeAdapter =
            ExcelFieldParseAdapter.getFieldAdapterByTypeStr(fieldTypeStr);
        IFieldAdapter<?> fieldAdapter = excelFieldTypeAdapter.getFieldAdapter();
        // 对枚举字段进行处理
        if (fieldAdapter instanceof EnumFieldAdapter) {
          excelFieldInfo = new ExcelEnumFieldInfo();
          excelFieldInfo.getFieldType().setFieldData(fieldTypeStr);
          // 获取枚举中的数据
          Set<String> enumFieldSet =
              ((EnumFieldAdapter) fieldAdapter).getEnumFieldSet(fieldTypeStr);
          ((ExcelEnumFieldInfo) excelFieldInfo).setEnumFieldData(enumFieldSet);
          // 设置枚举类名
          ((ExcelEnumFieldInfo) excelFieldInfo)
              .setEnumClassName(((EnumFieldAdapter) fieldAdapter).getEnumClassName(fieldTypeStr));
        }
      }
      // 加载字段名
      loadFieldInfoData(sheet, colNum, excelFieldInfo.getFieldName());
      if (StringUtils.isEmpty(excelFieldInfo.getFieldName().getFieldData())) {
        continue;
      } else {
        if (ExcelUtils.isIncorrectExcelFieldName(excelFieldInfo.getFieldName().getFieldData())) {
          throw new ExcelFormatCheckException(
              "字段名: " + excelFieldInfo.getFieldName().getFieldData() + "不符合规则,不能以数字或者特殊符号开头");
        }
      }
      excelFieldInfoMap.put(colNum, excelFieldInfo);
    }
    return excelFieldInfoMap;
  }

  /**
   * 加载sheet中某行excel字段信息
   *
   * @param sheet 工作薄
   * @param colNum 行数
   * @return excel字段信息
   */
  public static ExcelFieldInfo getExcelFieldInfo(Sheet sheet, int colNum) {
    ExcelFieldInfo excelFieldInfo = new ExcelFieldInfo();
    try {
      // 加载字段类型
      loadFieldInfoData(sheet, colNum, excelFieldInfo.getFieldType());
      if (StringUtils.isEmpty(excelFieldInfo.getFieldType().getFieldData())) {
        return null;
      } else {
        // 拿出字段类型后进行处理
        String fieldTypeStr = excelFieldInfo.getFieldType().getFieldData();
        ExcelFieldParseAdapter excelFieldTypeAdapter =
            ExcelFieldParseAdapter.getFieldAdapterByTypeStr(fieldTypeStr);
        IFieldAdapter<?> fieldAdapter = excelFieldTypeAdapter.getFieldAdapter();
        // 对枚举字段进行处理
        if (fieldAdapter instanceof EnumFieldAdapter) {
          excelFieldInfo = new ExcelEnumFieldInfo();
          Set<String> enumFieldSet =
              ((EnumFieldAdapter) fieldAdapter).getEnumFieldSet(fieldTypeStr);
          ((ExcelEnumFieldInfo) excelFieldInfo).setEnumFieldData(enumFieldSet);
          // 设置枚举类名
          ((ExcelEnumFieldInfo) excelFieldInfo)
              .setEnumClassName(((EnumFieldAdapter) fieldAdapter).getEnumClassName(fieldTypeStr));
        }
        String fieldType = fieldAdapter.getTargetFieldTypeStr(fieldTypeStr);
        excelFieldInfo.getFieldType().setFieldData(fieldType);
      }
      // 加载描述
      loadFieldInfoData(sheet, colNum, excelFieldInfo.getFieldDesc());
      if (StringUtils.isEmpty(excelFieldInfo.getFieldDesc().getFieldData())) {
        return null;
      }
      // 加载字段名
      loadFieldInfoData(sheet, colNum, excelFieldInfo.getFieldName());
      String fieldName = excelFieldInfo.getFieldName().getFieldData();
      if (StringUtils.isEmpty(fieldName)) {
        return null;
      } else {
        // 检查字段是否正确
        if (ExcelUtils.isIncorrectExcelFieldName(fieldName)) {
          throw new ExcelFormatCheckException("字段名: " + fieldName + "不符合规则,不能以数字或者特殊符号开头");
        }
        boolean isUnderLineTransUpper =
            SystemConfigHolder.getInstance()
                .getExcelConf()
                .getFieldRows()
                .getIsUnderLineTransUpper();
        // 如果需要将下划线后的字符转为大写
        if (isUnderLineTransUpper) {
          String transStr = StrUtils.upperCharWhenMeetSymbolReg(fieldName, '_');
          excelFieldInfo.getFieldName().setFieldData(transStr);
        }
      }
      // 加载字段值的范围
      loadFieldInfoData(sheet, colNum, excelFieldInfo.getFieldDataRange());
    } catch (BaseExcelException excelException) {
      excelException.appendExceptionStr(
          "文件名: " + sheet.getSheetName() + " 列: " + (colNum + 1) + " 解析excel字段时发生异常");
      throw excelException;
    }
    return excelFieldInfo;
  }

  /**
   * 加载单列字段信息
   *
   * @param sheet 工作簿
   * @param colNum 行号
   * @param metadata 字段元数据
   */
  public static void loadFieldInfoData(Sheet sheet, int colNum, FieldMetadata<String> metadata) {
    int configRowNum = metadata.getConfigBindRow();
    if (configRowNum < 0) {
      throw new ExcelParseException(
          "读取配置字段: "
              + metadata.getConfigBindRow()
              + " 列数: "
              + (colNum + 1)
              + " 配置表名: "
              + sheet.getSheetName()
              + " 错误, 配置的行数小于0");
    }
    if (sheet.getRow(configRowNum) != null) {
      Cell cell = sheet.getRow(configRowNum).getCell(colNum);
      String cellVal = null;
      if (cell != null) {
        String cellValue = ExcelUtils.getCellValue(cell);
        cellVal = cellValue.trim();
      }
      if (colNum == 0 && StringUtils.isEmpty(cellVal)) {
        // 兼容第一行不填的情况默认为int
        cellVal = "int";
      }
      if (cellVal != null) {
        metadata.setFieldData(cellVal.trim());
      }
    }
  }

  /** 关闭 */
  public static void onShutdown() {
    if (!EXCEL_GEN_EXECUTOR.isShutdown() && !EXCEL_GEN_EXECUTOR.isTerminated()) {
      EXCEL_GEN_EXECUTOR.shutdown();
    }
  }

  /** 获取需要跳过的列 */
  public static Set<Integer> getSkipCellList(Sheet sheet, String skipStr) {
    int dataFieldRangeRowNum =
        SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldDataRangeRow();
    Set<Integer> skipCellList = new HashSet<>();
    if (sheet == null) {
      return skipCellList;
    }
    Row dataFieldRangeRow = sheet.getRow(dataFieldRangeRowNum);
    if (dataFieldRangeRow == null || dataFieldRangeRow.getLastCellNum() <= 0) {
      return skipCellList;
    }
    for (Cell cell : dataFieldRangeRow) {
      String cellValue = ExcelUtils.getCellValue(cell);
      if (!StringUtils.isEmpty(cellValue) && skipStr.equalsIgnoreCase(cellValue)) {
        skipCellList.add(cell.getColumnIndex());
      }
    }
    return skipCellList;
  }
}
