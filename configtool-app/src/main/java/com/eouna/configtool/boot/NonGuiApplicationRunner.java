package com.eouna.configtool.boot;

import com.eouna.configtool.configholder.ConfigDataBean.ExcelGenPathConf;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.annotaion.AutoInject;
import com.eouna.configtool.core.boot.env.CommandLineAndArgs;
import com.eouna.configtool.core.boot.env.EnvironmentConstant;
import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.DataLoadSourceHolder;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.generator.LanguageDataLoadConfig;
import com.eouna.configtool.generator.MainEntryExportHolder;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.ToolsLoggerUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.scene.text.TextFlow;
import org.apache.commons.lang3.StringUtils;

/**
 * 非GUI(命令行)模式下的配置表生成逻辑。
 *
 * <p>由 {@link ConfigToolAppEntrance} 在以非GUI方式启动时调用, 复用界面同一套模板生成逻辑 ({@link
 * ExcelTemplateGenUtils#generateByTemplateParallel}), 因此两种模式产出一致。
 *
 * <p>参数需使用 {@code --key=value} 形式(与容器 {@link CommandLineAndArgs} 解析规则一致)。
 *
 * <p>支持的命令行参数:
 *
 * <ul>
 *   <li>{@code --mode=gui|none_gui} 启动模式, 不设置默认gui(由容器环境解析)
 *   <li>{@code --excel_path=./example} excel目录或单个excel文件, 支持相对路径(基于工作目录)
 *   <li>{@code --dest_path=./out} 导出目录, 支持相对路径(基于工作目录)
 *   <li>{@code --lang=java} 导出语言, 逗号分隔, 不设置默认java
 *   <li>{@code --load_source=excel|json} 数据加载方式, 多种语言时会自动协商共同支持的数据源
 * </ul>
 *
 * <p>示例: {@code ConfigTool --mode=none_gui --excel_path=./example --dest_path=./out --lang=go}
 *
 * <p>非GUI模式会固定追加json生成器, 保证至少完整走一次配置检查逻辑(字段/枚举/重复工作薄名校验)。
 *
 * @author CCL
 */
@Component
public class NonGuiApplicationRunner {

  /** excel目录/文件参数名, 支持相对路径 */
  public static final String ARG_EXCEL_PATH = "excel_path";

  /** 导出目录参数名, 支持相对路径 */
  public static final String ARG_DEST_PATH = "dest_path";

  /** 导出语言参数名, 逗号分隔, 不设置默认java */
  public static final String ARG_LANG = "lang";

  /** 数据读取方式参数名(excel/json), 默认excel */
  public static final String ARG_LOAD_SOURCE = "load_source";

  /** 导出模式参数名(client/server), 默认读取配置文件 */
  public static final String ARG_EXPORT_MODE = "export_mode";

  /** 是否导出主入口代码(true/false), 默认true */
  public static final String ARG_EXPORT_MAIN = "export_main";

  /** 默认导出语言 */
  private static final String DEFAULT_LANG = "java";

  /** 非GUI模式固定追加的生成器: json, 保证至少走一次配置检查逻辑 */
  private static final ETemplateGenerator CHECK_GENERATOR = ETemplateGenerator.JSON_GENERATOR;

  /** 等待生成完成的最长时间(分钟), 超时视为失败 */
  private static final long GEN_TIMEOUT_MINUTES = 30L;

  /** 用法说明(命令行帮助, 由 --help / --usage / -h 触发) */
  public static final String USAGE =
      String.join(
          System.lineSeparator(),
          "配置表工具 用法说明",
          "",
          "用法:",
          "  ConfigTool.exe [--mode=gui|none_gui] [选项...]",
          "",
          "启动模式:",
          "  --mode=gui        以图形界面启动(默认)",
          "  --mode=none_gui   以命令行方式启动, 生成完毕后退出",
          "                    兼容写法: nogui / commandline / command_line / cli / console",
          "",
          "命令行模式选项:",
          "  --excel_path=<路径>   excel目录或单个excel文件, 支持相对路径(基于工作目录)",
          "                        不设置时读取系统配置中的excel路径",
          "  --dest_path=<路径>    模板导出目录, 支持相对路径(基于工作目录)",
          "                        不设置时读取系统配置中的导出路径",
          "  --lang=<语言列表>     导出语言, 逗号分隔, 不设置默认java",
          "                        可选: java / json / go / rust / csharp / lua / cpp / js / ts",
          "                        例: --lang=java,go",
          "  --load_source=<方式>  数据加载选项: excel(默认) / json",
          "                        js/ts 只支持 json; 多语言组合会自动使用共同支持的数据源",
          "  --export_mode=<模式>  导出选项: client(客户端导出, 默认) / server(服务端导出)",
          "                        决定数据范围行中哪些字段不导出(跳过字段名见 ToolConfig.yaml 的 excelConf)",
          "                        不设置时读取 ToolConfig.yaml 的 exportConf.exportMode",
          "",
          "其它:",
          "  --help, --usage, -h   打印本用法说明并退出",
          "",
          "说明:",
          "  1. 参数需使用 --key=value 形式",
          "  2. 非GUI模式会固定追加json生成器, 保证至少完整走一次配置检查逻辑",
          "  3. 非GUI模式不会把excel_path/dest_path写回系统配置文件",
          "  4. js/ts 只支持 json; 例如 --lang=java,ts 会自动把数据源切换为 json",
          "  5. 退出码: 0成功, 1生成失败, 2未找到excel文件",
          "",
          "示例:",
          "  ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out",
          "  ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out --lang=go,rust");

  @AutoInject private SystemConfigHolder systemConfigHolder;

  /**
   * 执行非GUI模式的配置表生成流程。
   *
   * @param arguments 命令行参数
   * @return 进程退出码, 0为成功, 1为生成失败, 2为未找到excel文件
   */
  public int run(CommandLineAndArgs arguments) {
    // 请求帮助: 打印用法说明后退出
    if (isHelpRequested(arguments)) {
      System.out.println(USAGE);
      return 0;
    }
    // 非GUI模式: 错误信息只写日志, 不弹出窗口
    ToolsLoggerUtils.setHeadlessMode(true);
    // 非GUI模式不把命令行传入的路径落盘, 避免覆盖用户配置
    SystemConfigHolder.setConfigPersistEnabled(false);
    try {
      // 1. excel目录/文件, 支持相对路径
      File excelTarget = resolveExcelTarget(arguments);
      // 2. 导出目录, 支持相对路径
      String targetDir = applyTargetDir(arguments);
      // 3. 导出语言与数据读取方式(excel/json), 与界面「数据加载选项」等价
      List<String> langIds = resolveLangIds(arguments);
      String requestedSource = getOption(arguments, ARG_LOAD_SOURCE);
      if (StringUtils.isNotBlank(requestedSource)
          && !LanguageDataLoadConfig.isKnownSource(requestedSource)) {
        String error = "--load_source 取值错误: " + requestedSource + ", 只支持 excel / json";
        LoggerUtils.getLogger().error(error);
        System.err.println(error);
        return 1;
      }
      String loadSource = LanguageDataLoadConfig.resolveCommonSource(langIds, requestedSource);
      if (StringUtils.isBlank(loadSource)) {
        String error = "语言组合没有共同支持的数据源: " + langIds + ", 请检查 LanguageDataLoadConfig 配置";
        LoggerUtils.getLogger().error(error);
        System.err.println(error);
        return 1;
      }
      DataLoadSourceHolder.getInstance().setLoadSource(loadSource);
      // 非GUI固定追加json, 保证至少完整走一次配置检查逻辑
      Set<ETemplateGenerator> generators = resolveGenerators(langIds);
      LoggerUtils.getLogger()
          .info("数据读取方式: {}", DataLoadSourceHolder.getInstance().getLoadSource());
      // 导出模式(client/server), 与界面「导出选项」等价; 不设置时用配置文件里的值
      String exportMode = getOption(arguments, ARG_EXPORT_MODE);
      if (StringUtils.isNotBlank(exportMode)) {
        String modeValue = exportMode.trim();
        if (!DefaultEnvConfigConstant.EXPORT_MODE_CLIENT.equalsIgnoreCase(modeValue)
            && !DefaultEnvConfigConstant.EXPORT_MODE_SERVER.equalsIgnoreCase(modeValue)) {
          LoggerUtils.getLogger().error("--export_mode 取值错误: {}, 只支持 client / server", modeValue);
          System.err.println("--export_mode 取值错误: " + modeValue + ", 只支持 client / server");
          return 1;
        }
        systemConfigHolder
            .getExportConf()
            .setExportMode(
                DefaultEnvConfigConstant.EXPORT_MODE_SERVER.equalsIgnoreCase(modeValue)
                    ? DefaultEnvConfigConstant.EXPORT_MODE_SERVER
                    : DefaultEnvConfigConstant.EXPORT_MODE_CLIENT);
      }
      LoggerUtils.getLogger()
          .info(
              "导出模式: {}({}导出, 跳过数据范围行为 {} 的字段)",
              systemConfigHolder.getExportConf().getExportMode(),
              systemConfigHolder.isServerExport() ? "服务端" : "客户端",
              systemConfigHolder.getExportSkipStr());
      // 是否导出主入口代码(默认导出)
      String exportMain = getOption(arguments, ARG_EXPORT_MAIN);
      if (StringUtils.isNotBlank(exportMain)) {
        MainEntryExportHolder.getInstance()
            .setExportMainEntry(Boolean.parseBoolean(exportMain.trim()));
      }
      LoggerUtils.getLogger()
          .info("是否导出主入口代码: {}", MainEntryExportHolder.getInstance().isExportMainEntry());
      // 收集待生成的excel文件
      List<File> excelFileList = collectExcelFiles(excelTarget);
      if (excelFileList.isEmpty()) {
        LoggerUtils.getLogger().error("未找到excel文件: {}", excelTarget.getAbsolutePath());
        System.err.println("未找到excel文件: " + excelTarget.getAbsolutePath());
        return 2;
      }
      LoggerUtils.getLogger()
          .info(
              "非GUI模式开始生成, excel目录: {}, 导出目录: {}, 语言: {}",
              excelTarget.getAbsolutePath(),
              targetDir,
              generators);
      System.out.println("待生成excel文件: " + excelFileList);
      System.out.println("目标模板类型: " + generators);
      // 4. 复用界面并行生成逻辑
      boolean success = generate(generators, excelFileList);
      System.out.println("生成结果: " + (success ? "成功" : "失败"));
      return success ? 0 : 1;
    } catch (Exception e) {
      LoggerUtils.getLogger().error("非GUI模式生成异常", e);
      System.err.println("非GUI模式生成异常: " + e.getMessage());
      return 1;
    } finally {
      ToolsLoggerUtils.setHeadlessMode(false);
      SystemConfigHolder.setConfigPersistEnabled(true);
    }
  }

  /** 复用界面同一套并行生成逻辑, 阻塞等待生成结束 */
  private boolean generate(Set<ETemplateGenerator> generators, List<File> excelFileList) {
    TextAreaLogger textAreaLogger = new TextAreaLogger(new TextFlow());
    CountDownLatch finishedLatch = new CountDownLatch(1);
    AtomicBoolean success = new AtomicBoolean(false);
    try {
      ExcelTemplateGenUtils.generateByTemplateParallel(
          textAreaLogger,
          excelFileList,
          generators,
          result -> {
            success.set(result);
            finishedLatch.countDown();
          });
    } catch (Exception e) {
      LoggerUtils.getLogger().error("非GUI模式生成异常", e);
      return false;
    }
    try {
      if (!finishedLatch.await(GEN_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
        LoggerUtils.getLogger().error("非GUI模式生成超时, 等待超过 {} 分钟", GEN_TIMEOUT_MINUTES);
        return false;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LoggerUtils.getLogger().error("非GUI模式生成被中断", e);
      return false;
    }
    return success.get();
  }

  /** 解析excel目录/文件, 支持相对路径, 未设置时回退到系统配置 */
  private File resolveExcelTarget(CommandLineAndArgs arguments) {
    String excelPath = getOption(arguments, ARG_EXCEL_PATH);
    File excelTarget;
    if (StringUtils.isNotBlank(excelPath)) {
      excelTarget = new File(FileUtils.getAbsolutePath(excelPath));
    } else {
      excelTarget = new File(systemConfigHolder.getExcelConf().getPath().getExcelConfigLoadPath());
    }
    if (!excelTarget.exists()) {
      LoggerUtils.getLogger().error("excel目录/文件不存在: {}", excelTarget.getAbsolutePath());
      System.err.println("excel目录/文件不存在: " + excelTarget.getAbsolutePath());
    }
    return excelTarget;
  }

  /**
   * 应用导出目录, 支持相对路径。
   *
   * @return 解析后的绝对路径
   */
  private String applyTargetDir(CommandLineAndArgs arguments) {
    ExcelGenPathConf pathConf = systemConfigHolder.getExcelConf().getPath();
    String destPath = getOption(arguments, ARG_DEST_PATH);
    if (StringUtils.isNotBlank(destPath)) {
      pathConf.setTemplateFileGenTargetDir(destPath);
    }
    return pathConf.getTemplateFileGenTargetDir();
  }

  /** 解析导出语言, 不设置默认java。 */
  private List<String> resolveLangIds(CommandLineAndArgs arguments) {
    String langArg = getOption(arguments, ARG_LANG);
    String langValue = StringUtils.isBlank(langArg) ? DEFAULT_LANG : langArg;
    List<String> langIds = new ArrayList<>();
    for (String lang : langValue.split(",")) {
      String langName = lang.trim();
      if (!langName.isEmpty()) {
        langIds.add(langName);
      }
    }
    if (langIds.isEmpty()) {
      langIds.add(DEFAULT_LANG);
    }
    return langIds;
  }

  /** 解析导出语言, 非GUI模式固定追加json生成器。 */
  private Set<ETemplateGenerator> resolveGenerators(List<String> langIds) {
    EnumSet<ETemplateGenerator> generators = EnumSet.noneOf(ETemplateGenerator.class);
    for (String langName : langIds) {
      generators.add(ETemplateGenerator.getTemplateGeneratorByType(langName));
    }
    // 非GUI模式固定追加json, 保证至少完整走一次配置检查逻辑
    generators.add(CHECK_GENERATOR);
    return generators;
  }

  /** 收集excel文件, 目录则收集其中所有xlsx, 文件则直接使用 */
  private List<File> collectExcelFiles(File excelTarget) {
    Map<String, File> excelFileMap = new TreeMap<>();
    if (excelTarget.isFile()) {
      excelFileMap.put(excelTarget.getAbsolutePath(), excelTarget);
    } else {
      File[] files = excelTarget.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));
      if (files != null) {
        for (File file : files) {
          excelFileMap.put(file.getAbsolutePath(), file);
        }
      }
    }
    return new ArrayList<>(excelFileMap.values());
  }

  /** 是否请求了帮助/用法说明 */
  private boolean isHelpRequested(CommandLineAndArgs arguments) {
    for (String helpName : new String[] {EnvironmentConstant.COMMAND_LINE_ARG_HELP, "usage", "h"}) {
      if (arguments != null && arguments.containOptionName(helpName)) {
        return true;
      }
    }
    return false;
  }

  /** 读取选项的第一个值, 不存在返回null */
  private String getOption(CommandLineAndArgs arguments, String optionName) {
    if (arguments == null) {
      return null;
    }
    List<String> values = arguments.getOptionArgs(optionName);
    return values.isEmpty() ? null : values.get(0);
  }
}
