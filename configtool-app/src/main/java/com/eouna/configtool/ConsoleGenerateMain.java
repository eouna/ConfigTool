package com.eouna.configtool;

import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.generator.base.ExcelFileStructure;
import com.eouna.configtool.generator.bean.ExcelSheetBean;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.utils.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.scene.text.TextFlow;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * 命令行模板生成入口(无界面)。
 *
 * <p>用法: {@code ConsoleGenerateMain <模板类型,逗号分隔> [--parallel] [excel文件或目录 ...]}.
 *
 * <p>示例:
 *
 * <pre>
 * ConsoleGenerateMain go example/example.xlsx example/subconfig_two.xlsx
 * ConsoleGenerateMain go --parallel example/example.xlsx example/subconfig_two.xlsx
 * ConsoleGenerateMain java,go example
 * </pre>
 *
 * <p>{@code --parallel} 与界面上的生成逻辑一致(多线程并行生成), 其余情况为顺序生成, 便于对比排查。
 *
 * @author CCL
 */
public class ConsoleGenerateMain {

  public static void main(String[] args) throws Exception {
    String langArg = args != null && args.length > 0 && !args[0].isBlank() ? args[0] : "go";
    boolean parallel = false;
    List<String> excelArgs = new ArrayList<>();
    if (args != null) {
      for (int i = 1; i < args.length; i++) {
        String arg = args[i];
        if (arg == null || arg.isBlank()) {
          continue;
        }
        if ("--parallel".equalsIgnoreCase(arg)) {
          parallel = true;
        } else {
          excelArgs.add(arg);
        }
      }
    }

    Set<ETemplateGenerator> generators =
        Arrays.stream(langArg.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(ETemplateGenerator::getTemplateGeneratorByType)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(ETemplateGenerator.class)));

    // 初始化JavaFX线程工具(日志使用Platform.runLater)
    Platform.startup(() -> {});
    SystemConfigHolder.getInstance().loadSystemConfig();

    Map<String, File> excelFileMap = new TreeMap<>();
    if (excelArgs.isEmpty()) {
      collectExcelFiles(excelFileMap, resolveExcelTarget(null));
    } else {
      for (String excelArg : excelArgs) {
        collectExcelFiles(excelFileMap, new File(excelArg));
      }
    }
    if (excelFileMap.isEmpty()) {
      System.err.println("未找到excel文件: " + excelArgs);
      System.exit(2);
    }
    System.out.println("待生成excel文件: " + excelFileMap.values());
    System.out.println("目标模板类型: " + generators + ", 并行生成: " + parallel);

    boolean result = generate(generators, excelFileMap.values(), parallel);
    System.out.println("生成结果: " + (result ? "成功" : "失败"));
    Platform.exit();
    System.exit(result ? 0 : 1);
  }

  /** 无界面模板生成流程(等价于界面触发逻辑, 但不依赖窗口控制器) */
  private static boolean generate(
      Set<ETemplateGenerator> generators, Collection<File> excelFileList, boolean parallel)
      throws InterruptedException {
    TextAreaLogger logger = new TextAreaLogger(new TextFlow());
    List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
    List<File> successGenList = parallel ? new CopyOnWriteArrayList<>() : new ArrayList<>();
    try {
      prepareTargetDir();
      Map<File, ExcelFileStructure> excelFileStructure =
          ExcelTemplateGenUtils.buildExcelFileStructure(excelFileList);
      // 生成前(基础类/父表)
      for (ETemplateGenerator generator : generators) {
        generator
            .getTemplateGenerator()
            .generatorBefore(successGenList, excelFileStructure, exceptions);
      }
      if (exceptions.isEmpty()) {
        // 只处理真实存在的文件, 虚拟父节点已在generatorBefore中生成
        List<File> realFiles =
            excelFileStructure.keySet().stream()
                .filter(File::exists)
                .collect(Collectors.toList());
        if (parallel) {
          int threadNum = Math.max(2, Math.min(realFiles.size(), Runtime.getRuntime().availableProcessors()));
          ExecutorService executor = Executors.newFixedThreadPool(threadNum);
          CountDownLatch latch = new CountDownLatch(realFiles.size());
          for (File file : realFiles) {
            executor.execute(
                () -> {
                  try {
                    generateOneFile(
                        file, excelFileStructure.get(file), generators, successGenList);
                  } catch (Exception e) {
                    exceptions.add(e);
                  } finally {
                    latch.countDown();
                  }
                });
          }
          latch.await();
          executor.shutdown();
        } else {
          for (File file : realFiles) {
            try {
              generateOneFile(file, excelFileStructure.get(file), generators, successGenList);
            } catch (Exception e) {
              exceptions.add(e);
            }
          }
        }
      }
      // 生成后(数据管理器/枚举/调试入口)
      if (exceptions.isEmpty()) {
        for (ETemplateGenerator generator : generators) {
          generator.getTemplateGenerator().generatorAfter(logger, excelFileStructure);
        }
      }
    } catch (Exception e) {
      exceptions.add(e);
    }
    for (Exception exception : exceptions) {
      System.err.println("生成异常: " + exception.getMessage());
      exception.printStackTrace();
    }
    return exceptions.isEmpty();
  }

  /** 生成单个excel文件 */
  private static void generateOneFile(
      File file,
      ExcelFileStructure excelFileStructure,
      Set<ETemplateGenerator> generators,
      List<File> successGenList)
      throws Exception {
    try (Workbook workbook = WorkbookFactory.create(file, null, true)) {
      Sheet sheet = workbook.getSheetAt(0);
      ExcelSheetBean sheetBean = new ExcelSheetBean(file, sheet);
      for (ETemplateGenerator generator : generators) {
        generator
            .getTemplateGenerator()
            .generatorOneExcelFile(file, workbook, sheet, sheetBean, excelFileStructure);
      }
      successGenList.add(file);
    }
  }

  /** 清理并初始化模板输出目录 */
  private static void prepareTargetDir() throws Exception {
    String targetDir =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir();
    File targetFile = new File(targetDir);
    FileUtils.getOrCreateDir(targetDir);
    org.apache.commons.io.FileUtils.cleanDirectory(targetFile);
  }

  /** 解析excel目标路径 */
  private static File resolveExcelTarget(String excelArg) {
    if (excelArg != null && !excelArg.isBlank()) {
      return new File(excelArg);
    }
    String excelLoadPath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath();
    return new File(excelLoadPath);
  }

  /** 收集excel文件 */
  private static void collectExcelFiles(Map<String, File> result, File excelTarget) {
    if (excelTarget.isFile()) {
      result.put(excelTarget.getAbsolutePath(), excelTarget);
      return;
    }
    File[] files = excelTarget.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx"));
    if (files == null) {
      return;
    }
    for (File file : files) {
      result.put(file.getAbsolutePath(), file);
    }
  }
}