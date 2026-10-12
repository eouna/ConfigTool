package com.eouna.configtool.generator;

import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.utils.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 各语言模板"主入口"的支持信息。
 *
 * <p>每种语言都有独立的主函数模板(DebugMain.ftl / Main.ftl), 生成后可直接运行; 第二步的预加载就是
 * 执行这些主入口来校验配置数据。本类负责:
 *
 * <ul>
 *   <li>主入口文件在导出目录中的相对路径(用于删除主入口代码)
 *   <li>运行主入口的工作目录与命令(数据读取路径由调用方按"语言加载选项"传入)
 * </ul>
 *
 * @author CCL
 */
public final class TemplateMainEntrySupport {

  /** 语言标识 -> 主入口文件(相对导出目录) */
  private static final Map<String, List<String>> MAIN_ENTRY_PATHS = new LinkedHashMap<>();

  /** 语言标识 -> 主入口运行目录(相对导出目录) */
  private static final Map<String, String> RUN_DIRS = new LinkedHashMap<>();

  /** 不支持运行的语言(纯数据模板) */
  private static final List<String> NO_RUNTIME_LANGS = Collections.singletonList("json");

  static {
    // java: 主入口由 Main.ftl 生成, 编译+运行
    RUN_DIRS.put("java", "java");
    MAIN_ENTRY_PATHS.put("java", Collections.singletonList("java"));
    // go: go/debug/main.go
    RUN_DIRS.put("go", "go");
    MAIN_ENTRY_PATHS.put("go", Collections.singletonList("go/debug"));
    // rust: rust/src/main.rs
    RUN_DIRS.put("rust", "rust");
    MAIN_ENTRY_PATHS.put("rust", Collections.singletonList("rust/src/main.rs"));
    // csharp: csharp/src/Program.cs
    RUN_DIRS.put("csharp", "csharp");
    MAIN_ENTRY_PATHS.put("csharp", Collections.singletonList("csharp/src/Program.cs"));
    // lua: lua/main.lua
    RUN_DIRS.put("lua", "lua");
    MAIN_ENTRY_PATHS.put("lua", Collections.singletonList("lua/main.lua"));
    // cpp: cpp/main.cpp
    RUN_DIRS.put("cpp", "cpp");
    MAIN_ENTRY_PATHS.put("cpp", Collections.singletonList("cpp/main.cpp"));
    // js: js/debug/main.js
    RUN_DIRS.put("js", "js");
    MAIN_ENTRY_PATHS.put("js", Collections.singletonList("js/debug/main.js"));
  }

  private TemplateMainEntrySupport() {}

  /**
   * 该语言是否可以执行主入口完成预加载
   *
   * @param langId 语言标识
   * @return 是否支持
   */
  public static boolean isPreloadSupported(String langId) {
    return langId != null
        && !NO_RUNTIME_LANGS.contains(langId.toLowerCase(Locale.ROOT))
        && RUN_DIRS.containsKey(langId.toLowerCase(Locale.ROOT));
  }

  /** 主入口在导出目录中的相对路径列表 */
  public static List<String> getMainEntryPaths(String langId) {
    if (langId == null) {
      return Collections.emptyList();
    }
    return MAIN_ENTRY_PATHS.getOrDefault(langId.toLowerCase(Locale.ROOT), Collections.emptyList());
  }

  /**
   * 解析主入口文件(用于删除)
   *
   * @param langId 语言标识
   * @param outputDir 模板导出目录
   * @return 实际存在的主入口文件列表
   */
  public static List<File> resolveMainEntryFiles(String langId, File outputDir) {
    List<File> files = new ArrayList<>();
    for (String relativePath : getMainEntryPaths(langId)) {
      File entry = new File(outputDir, relativePath);
      if (!entry.exists()) {
        continue;
      }
      if (entry.isDirectory()) {
        File[] children = entry.listFiles();
        if (children != null) {
          files.addAll(Arrays.stream(children).filter(File::isFile).collect(Collectors.toList()));
        }
      } else {
        files.add(entry);
      }
    }
    return files;
  }

  /**
   * 构造预加载执行计划(编译 + 运行)
   *
   * @param langId 语言标识
   * @param outputDir 模板导出目录
   * @param dataPath 本次要读取的数据路径(由"语言加载选项"决定: excel目录 或 json目录)
   * @return 执行计划, 不支持时返回null
   */
  public static PreloadPlan createPlan(String langId, File outputDir, String dataPath) {
    if (!isPreloadSupported(langId)) {
      return null;
    }
    String lang = langId.toLowerCase(Locale.ROOT);
    File workDir = new File(outputDir, RUN_DIRS.get(lang));
    List<List<String>> commands = new ArrayList<>();
    switch (lang) {
      case "java":
        {
          File buildDir = new File(workDir, "build");
          List<String> javaFiles = collectFiles(workDir, ".java");
          if (javaFiles.isEmpty()) {
            return null;
          }
          // IDE 下 java.class.path 可能只包含模块路径之外的部分依赖, 这里补齐随程序分发的热加载依赖库。
          String classpath = buildJavaClasspath();
          List<String> compile = new ArrayList<>();
          compile.add("javac");
          compile.add("-encoding");
          compile.add("UTF-8");
          compile.add("-nowarn");
          compile.add("-cp");
          compile.add(classpath);
          compile.add("-d");
          compile.add(buildDir.getAbsolutePath());
          compile.addAll(javaFiles);
          commands.add(compile);

          String mainClass =
              SystemConfigHolder.getInstance().getJavaTemplateConf().getPackageName()
                  + "."
                  + SystemConfigHolder.getInstance().getJavaTemplateConf().getDataManagerClassName()
                  + "Main";
          commands.add(
              Arrays.asList(
                  "java",
                  "-cp",
                  classpath + File.pathSeparator + buildDir.getAbsolutePath(),
                  mainClass,
                  dataPath));
          break;
        }
      case "go":
        // 生成的go工程需要先补齐依赖(go.sum), 再运行主入口
        commands.add(Arrays.asList("go", "mod", "tidy"));
        commands.add(Arrays.asList("go", "run", "./debug", dataPath));
        break;
      case "rust":
        commands.add(Arrays.asList("cargo", "run", "--quiet", "--", dataPath));
        break;
      case "csharp":
        commands.add(Arrays.asList("dotnet", "run", "--", dataPath));
        break;
      case "lua":
        commands.add(Arrays.asList("lua", "main.lua", dataPath));
        break;
      case "js":
        commands.add(Arrays.asList("node", "debug/main.js", dataPath));
        break;
      case "cpp":
        {
          List<String> sources = collectFiles(workDir, ".cpp");
          List<String> cSources = collectFiles(new File(workDir, "third_party"), ".c");
          List<String> compile = new ArrayList<>();
          compile.add("g++");
          compile.add("-std=c++17");
          compile.add("-O0");
          compile.add("-Iinclude");
          compile.add("-I.");
          compile.add("-Ithird_party");
          compile.add("-o");
          compile.add("config_tool_cpp");
          compile.addAll(sources.stream().map(f -> new File(f).getName()).collect(Collectors.toList()));
          compile.addAll(
              cSources.stream()
                  .map(f -> "third_party/" + new File(f).getName())
                  .collect(Collectors.toList()));
          commands.add(compile);
          commands.add(Arrays.asList("./config_tool_cpp", dataPath));
          break;
        }
      default:
        return null;
    }
    return new PreloadPlan(workDir, commands);
  }

  /** 组装 Java 编译/运行所需类路径(运行期classpath + templatelib/*.jar) */
  private static String buildJavaClasspath() {
    Set<String> entries = new LinkedHashSet<>();
    String runtimeClasspath = System.getProperty("java.class.path");
    if (runtimeClasspath != null && !runtimeClasspath.isBlank()) {
      for (String entry : runtimeClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
        if (!entry.isBlank()) {
          entries.add(entry);
        }
      }
    }

    File libDir =
        new File(FileUtils.getRootDir(), DefaultEnvConfigConstant.DIR_JAVA_LIB_HOT_RELOAD_DEPEND_ON);
    File[] jarFiles =
        libDir.listFiles(
            (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
    if (jarFiles != null && jarFiles.length > 0) {
      Arrays.sort(
          jarFiles, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
      for (File jarFile : jarFiles) {
        entries.add(jarFile.getAbsolutePath());
      }
    } else {
      LoggerUtils.getLogger().warn("未找到Java预加载依赖目录: {}", libDir.getAbsolutePath());
    }
    return String.join(File.pathSeparator, entries);
  }

  /** 收集指定后缀的文件(递归, 返回绝对路径) */
  private static List<String> collectFiles(File dir, String suffix) {
    if (dir == null || !dir.isDirectory()) {
      return Collections.emptyList();
    }
    try (Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(dir.toPath())) {
      return stream
          .filter(java.nio.file.Files::isRegularFile)
          .map(java.nio.file.Path::toString)
          .filter(path -> path.toLowerCase(Locale.ROOT).endsWith(suffix))
          .collect(Collectors.toList());
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  /** 预加载执行计划 */
  public static class PreloadPlan {
    private final File workDir;
    private final List<List<String>> commands;

    PreloadPlan(File workDir, List<List<String>> commands) {
      this.workDir = workDir;
      this.commands = commands;
    }

    public File getWorkDir() {
      return workDir;
    }

    public List<List<String>> getCommands() {
      return commands;
    }

    /** 命令的可读形式, 用于日志 */
    public String toDisplayString() {
      return commands.stream().map(cmd -> String.join(" ", cmd)).collect(Collectors.joining(" && "));
    }
  }
}
