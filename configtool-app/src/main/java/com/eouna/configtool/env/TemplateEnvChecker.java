package com.eouna.configtool.env;

import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.generator.template.ETemplateGenerator;
import com.eouna.configtool.utils.ProcessCommandUtils;
import com.eouna.configtool.utils.ProcessOutputUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 各导出语言模板的运行环境检测。
 *
 * <p>检测方式: 尝试执行对应工具链的版本命令, 能正常输出即视为可用。检测结果由 {@link TemplateEnvContext} 缓存在内存中。
 *
 * <p>本类通过 {@code @Component} 注册到容器中, 使用时请通过 {@code @AutoInject} 注入, 不要再新增静态单例。
 *
 * @author CCL
 */
@Component
public class TemplateEnvChecker {

  /** 单个命令的超时时间(秒) */
  private static final int PROBE_TIMEOUT_SECONDS = 5;

  /** 每种语言用于探测的命令(按顺序尝试, 命中即返回) */
  private static final Map<String, List<String[]>> PROBE_COMMANDS = new LinkedHashMap<>();

  /** 语言展示名 */
  private static final Map<String, String> LANG_NAMES = new LinkedHashMap<>();

  static {
    // java: 生成的是源码, 需要JDK(优先javac, 退回java)
    LANG_NAMES.put("java", "Java");
    PROBE_COMMANDS.put(
        "java", List.of(new String[] {"javac", "-version"}, new String[] {"java", "-version"}));
    // json: 纯数据, 不需要运行环境
    LANG_NAMES.put("json", "JSON");
    PROBE_COMMANDS.put("json", List.<String[]>of());
    // go
    LANG_NAMES.put("go", "Go");
    PROBE_COMMANDS.put("go", List.<String[]>of(new String[] {"go", "version"}));
    // rust
    LANG_NAMES.put("rust", "Rust");
    PROBE_COMMANDS.put(
        "rust", List.of(new String[] {"cargo", "--version"}, new String[] {"rustc", "--version"}));
    // C#
    LANG_NAMES.put("csharp", "C#");
    PROBE_COMMANDS.put(
        "csharp", List.of(new String[] {"dotnet", "--version"}, new String[] {"csc", "-version"}));
    // lua
    LANG_NAMES.put("lua", "Lua");
    PROBE_COMMANDS.put(
        "lua",
        List.of(
            new String[] {"lua", "-v"},
            new String[] {"lua54", "-v"},
            new String[] {"lua5.4", "-v"},
            new String[] {"luajit", "-v"}));
    // C++
    LANG_NAMES.put("cpp", "C++");
    PROBE_COMMANDS.put(
        "cpp",
        List.of(
            new String[] {"g++", "--version"},
            new String[] {"gcc", "--version"},
            new String[] {"clang++", "--version"},
            new String[] {"cl", "/?"}));
    // javascript
    LANG_NAMES.put("js", "JavaScript");
    PROBE_COMMANDS.put(
        "js", List.of(new String[] {"node", "--version"}, new String[] {"nodejs", "--version"}));
    // typescript: 优先使用全局 tsc; 没有全局命令时, 可通过 npm 安装工程内 TypeScript 依赖后构建
    LANG_NAMES.put("ts", "TypeScript");
    PROBE_COMMANDS.put(
        "ts",
        List.of(
            new String[] {"tsc", "--version"},
            new String[] {"npm", "--version"},
            new String[] {"npm.cmd", "--version"}));
  }

  public TemplateEnvChecker() {}

  /**
   * 检测所有支持导出的语言模板的运行环境。
   *
   * @return 语言标识 -> 检测结果
   */
  public Map<String, TemplateEnvResult> checkAll() {
    List<String> langIds = getSupportedLangIds();
    List<Callable<TemplateEnvResult>> tasks = new ArrayList<>(langIds.size());
    for (String langId : langIds) {
      tasks.add(() -> checkOne(langId));
    }
    Map<String, TemplateEnvResult> results = new LinkedHashMap<>();
    ExecutorService executor = newExecutor(tasks.size());
    try {
      // 并行检测, 避免逐个等待导致启动变慢
      List<Future<TemplateEnvResult>> futures =
          executor.invokeAll(tasks, PROBE_TIMEOUT_SECONDS + 5L, TimeUnit.SECONDS);
      for (int i = 0; i < futures.size(); i++) {
        String langId = langIds.get(i);
        try {
          Future<TemplateEnvResult> future = futures.get(i);
          TemplateEnvResult result = future.isCancelled() ? null : future.get();
          results.put(langId, result == null ? notAvailable(langId, "检测超时") : result);
        } catch (Exception e) {
          LoggerUtils.getLogger().warn("检测语言模板运行环境失败: {}", langId, e);
          results.put(langId, notAvailable(langId, "检测异常: " + e.getMessage()));
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LoggerUtils.getLogger().warn("检测语言模板运行环境被中断", e);
      for (String langId : langIds) {
        results.putIfAbsent(langId, notAvailable(langId, "检测被中断"));
      }
    } finally {
      executor.shutdownNow();
    }
    return results;
  }

  /** 获取所有支持导出的语言标识(取自模板生成器枚举, 新增语言会自动纳入检测) */
  public List<String> getSupportedLangIds() {
    List<String> langIds = new ArrayList<>();
    for (ETemplateGenerator generator : ETemplateGenerator.values()) {
      langIds.add(generator.getTemplateHandler().getTemplateBindRelatedPath());
    }
    return langIds;
  }

  /** 检测单个语言 */
  private static TemplateEnvResult checkOne(String langId) {
    String langName = LANG_NAMES.getOrDefault(langId, langId);
    List<String[]> commands = PROBE_COMMANDS.get(langId);
    if (commands == null || commands.isEmpty()) {
      // 没有探测命令的语言(如json)视为不需要运行环境
      return new TemplateEnvResult(langId, langName, false, true, "-", "-", "", "该语言模板无需运行环境");
    }
    List<String> triedCommands = new ArrayList<>();
    for (String[] command : commands) {
      triedCommands.add(String.join(" ", command));
      String executable = resolveExecutable(command[0]);
      String[] realCommand = command.clone();
      if (executable != null) {
        realCommand[0] = executable;
      }
      ProbeOutput output = execute(realCommand);
      if (output != null && output.hasOutput()) {
        return new TemplateEnvResult(
            langId,
            langName,
            true,
            true,
            String.join(" ", command),
            executable == null ? command[0] : executable,
            output.firstLine(),
            "环境可用");
      }
    }
    return new TemplateEnvResult(
        langId,
        langName,
        true,
        false,
        String.join(" 或 ", triedCommands),
        "-",
        "",
        "未检测到运行环境, 生成该语言后需自行安装对应工具链");
  }

  private static TemplateEnvResult notAvailable(String langId, String message) {
    return new TemplateEnvResult(
        langId, LANG_NAMES.getOrDefault(langId, langId), true, false, "-", "-", "", message);
  }

  /**
   * 执行探测命令(带超时, 读取输出后立即结束进程)
   *
   * @param command 命令与参数
   * @return 输出, 执行失败返回null
   */
  private static ProbeOutput execute(String[] command) {
    Process process = null;
    try {
      ProcessBuilder processBuilder = new ProcessBuilder(ProcessCommandUtils.prepare(command));
      processBuilder.redirectErrorStream(true);
      process = processBuilder.start();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      Process currentProcess = process;
      Thread reader =
          new Thread(
              () -> {
                try (InputStream inputStream = currentProcess.getInputStream()) {
                  inputStream.transferTo(output);
                } catch (IOException ignored) {
                  // 进程被销毁时读取会中断, 忽略
                }
              },
              "template-env-probe-reader");
      reader.setDaemon(true);
      reader.start();
      if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return null;
      }
      reader.join(1000);
      // 各工具链输出编码不统一(如MSVC的cl输出GBK), 统一走解码工具, 避免预加载页与日志出现乱码
      return new ProbeOutput(ProcessOutputUtils.decode(output.toByteArray()));
    } catch (IOException e) {
      // 命令不存在
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  /**
   * 在PATH中查找可执行文件, 便于记录实际使用的工具路径
   *
   * @param name 可执行文件名(不带后缀)
   * @return 完整路径, 未找到返回null
   */
  private static String resolveExecutable(String name) {
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null || pathEnv.isEmpty()) {
      return null;
    }
    boolean windows =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    List<String> extensions = new ArrayList<>();
    if (windows) {
      String pathExt = System.getenv("PATHEXT");
      if (pathExt == null || pathExt.isEmpty()) {
        pathExt = ".COM;.EXE;.BAT;.CMD";
      }
      for (String ext : pathExt.split(";")) {
        if (!ext.isEmpty()) {
          extensions.add(ext.toLowerCase(Locale.ROOT));
        }
      }
      if (!name.toLowerCase(Locale.ROOT).endsWith(".exe")) {
        extensions.add(0, ".exe");
      }
    } else {
      extensions.add("");
    }
    for (String dir : pathEnv.split(File.pathSeparator)) {
      if (dir.isEmpty()) {
        continue;
      }
      for (String ext : extensions) {
        File candidate = new File(dir, name + ext);
        if (candidate.isFile()) {
          return candidate.getAbsolutePath();
        }
      }
      File rawCandidate = new File(dir, name);
      if (rawCandidate.isFile()) {
        return rawCandidate.getAbsolutePath();
      }
    }
    return null;
  }

  private static ExecutorService newExecutor(int size) {
    AtomicInteger counter = new AtomicInteger();
    ThreadFactory threadFactory =
        runnable -> {
          Thread thread = new Thread(runnable, "template-env-check-" + counter.incrementAndGet());
          thread.setDaemon(true);
          return thread;
        };
    return Executors.newFixedThreadPool(Math.max(1, size), threadFactory);
  }

  /** 命令输出 */
  private static class ProbeOutput {
    private final String content;

    ProbeOutput(String content) {
      this.content = content == null ? "" : content.trim();
    }

    boolean hasOutput() {
      return !content.isEmpty();
    }

    String firstLine() {
      int index = content.indexOf('\n');
      String line = index < 0 ? content : content.substring(0, index);
      line = line.trim();
      return line.length() > 200 ? line.substring(0, 200) : line;
    }
  }
}
