package com.eouna.configtool.generator;

import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.env.TemplateEnvResult;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.ProcessCommandUtils;
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
 * <p>每种语言都有独立的主函数模板(DebugMain.ftl / Main.ftl), 生成后可直接运行; 第二步的预加载就是 执行这些主入口来校验配置数据。本类负责:
 *
 * <ul>
 *   <li>主入口文件在导出目录中的相对路径(用于删除主入口代码)
 *   <li>运行主入口的工作目录与命令(数据读取路径由调用方按"数据加载选项"传入)
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

  /** C++编译器候选(按优先级), 与环境检测的探测顺序保持一致 */
  private static final List<String> CPP_COMPILER_NAMES =
      Arrays.asList("g++", "gcc", "clang++", "cl");

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
    // ts: ts/src/debug/main.ts, 编译后运行 ts/dist/debug/main.js
    RUN_DIRS.put("ts", "ts");
    MAIN_ENTRY_PATHS.put("ts", Collections.singletonList("ts/src/debug/main.ts"));
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
   * @param dataPath 本次要读取的数据路径(由"数据加载选项"决定: excel目录 或 json目录)
   * @param envResult 该语言的运行环境检测结果, 可为null
   * @return 执行计划, 不支持或缺少工具链时返回null
   */
  public static PreloadPlan createPlan(
      String langId, File outputDir, String dataPath, TemplateEnvResult envResult) {
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
      case "ts":
        {
          List<List<String>> tsCommands = createTypeScriptCommands(workDir, dataPath, envResult);
          if (tsCommands == null) {
            return null;
          }
          commands.addAll(tsCommands);
          break;
        }
      case "cpp":
        {
          List<List<String>> cppCommands = createCppCommands(workDir, dataPath, envResult);
          if (cppCommands == null) {
            return null;
          }
          commands.addAll(cppCommands);
          break;
        }
      default:
        return null;
    }
    return new PreloadPlan(
        workDir, commands.stream().map(ProcessCommandUtils::prepare).collect(Collectors.toList()));
  }

  /** 构建 TypeScript 的安装/编译与运行命令 */
  private static List<List<String>> createTypeScriptCommands(
      File workDir, String dataPath, TemplateEnvResult envResult) {
    boolean useNpm = usesNpmForTypeScript(envResult);
    String tool = resolveTypeScriptTool(useNpm, envResult);
    if (tool == null) {
      LoggerUtils.getLogger().warn("未找到可用的 TypeScript 或 npm, 无法预加载");
      return null;
    }
    List<List<String>> commands = new ArrayList<>(3);
    if (useNpm) {
      commands.add(Arrays.asList(tool, "install", "--no-audit", "--no-fund"));
      commands.add(Arrays.asList(tool, "run", "build"));
    } else {
      commands.add(Arrays.asList(tool, "-p", "."));
    }
    commands.add(Arrays.asList("node", "dist/debug/main.js", dataPath));
    return commands;
  }

  /** 环境检测是否退回到了 npm */
  private static boolean usesNpmForTypeScript(TemplateEnvResult envResult) {
    if (envResult == null) {
      return false;
    }
    String command = envResult.getCommand();
    if (command != null && command.trim().toLowerCase(Locale.ROOT).startsWith("npm")) {
      return true;
    }
    String executable = envResult.getExecutablePath();
    if (executable == null || executable.isBlank() || "-".equals(executable)) {
      return false;
    }
    String fileName = new File(executable).getName().toLowerCase(Locale.ROOT);
    return fileName.startsWith("npm");
  }

  /** 获取实际可执行的 tsc/npm 路径 */
  private static String resolveTypeScriptTool(boolean useNpm, TemplateEnvResult envResult) {
    if (envResult != null) {
      String executable = envResult.getExecutablePath();
      if (executable != null && !executable.isBlank() && !"-".equals(executable)) {
        return executable;
      }
    }
    String toolName = useNpm ? "npm" : "tsc";
    String found = findInPath(toolName);
    return found == null ? toolName : found;
  }

  /**
   * 构建C++的编译与运行命令
   *
   * <p>不同工具链的参数风格不同: g++/clang++ 用 POSIX 风格, MSVC(cl) 用 / 开头的选项, 且 MSVC 必须先加载 vcvars 环境否则找不到标准库头文件。
   *
   * @param workDir cpp工程目录
   * @param dataPath 预加载数据路径
   * @param envResult 该语言的环境检测结果, 可为null
   * @return 命令列表, 未找到可用编译器时返回null
   */
  private static List<List<String>> createCppCommands(
      File workDir, String dataPath, TemplateEnvResult envResult) {
    CppCompiler compiler = resolveCppCompiler(envResult);
    if (compiler == null) {
      LoggerUtils.getLogger().warn("未找到可用的C++编译器, 无法预加载");
      return null;
    }
    List<String> sources = new ArrayList<>(collectFiles(workDir, ".cpp"));
    if (sources.isEmpty()) {
      return null;
    }
    sources.addAll(collectFiles(new File(workDir, "third_party"), ".c"));
    String executableName = isWindows() ? "config_tool_cpp.exe" : "config_tool_cpp";
    List<List<String>> commands = new ArrayList<>(2);
    List<String> compile = new ArrayList<>();
    compile.add(compiler.getExecutable());
    if (compiler.isMsvc()) {
      compile.add("/nologo");
      compile.add("/std:c++17");
      compile.add("/EHsc");
      // 生成的源码包含中文, 强制按UTF-8编译, 避免C4819警告与中文乱码
      compile.add("/utf-8");
      compile.add("/Iinclude");
      compile.add("/I.");
      compile.add("/Ithird_party");
      compile.add("/Fe:" + executableName);
      compile.addAll(sources);
      String vcvarsPrefix = buildVcvarsCommandPrefix(compiler.getExecutable());
      if (vcvarsPrefix == null) {
        // 找不到vcvars时直接调用, 依赖当前已处于VS开发环境
        commands.add(compile);
      } else {
        commands.add(Arrays.asList("cmd", "/c", vcvarsPrefix + toCommandLine(compile)));
      }
    } else {
      compile.add("-std=c++17");
      compile.add("-O0");
      compile.add("-Iinclude");
      compile.add("-I.");
      compile.add("-Ithird_party");
      compile.add("-o");
      compile.add(executableName);
      compile.addAll(sources);
      commands.add(compile);
    }
    // 直接按绝对路径启动编译产物, 避免依赖工作目录与不同平台的相对路径解析
    commands.add(Arrays.asList(new File(workDir, executableName).getAbsolutePath(), dataPath));
    return commands;
  }

  /**
   * 解析可用的C++编译器
   *
   * @param envResult 环境检测结果, 优先使用其中实际命中的可执行文件
   * @return 编译器信息, 未找到返回null
   */
  private static CppCompiler resolveCppCompiler(TemplateEnvResult envResult) {
    List<String> candidates = new ArrayList<>(4);
    if (envResult != null && envResult.isAvailable()) {
      String executablePath = envResult.getExecutablePath();
      if (executablePath != null && !executablePath.isBlank() && !"-".equals(executablePath)) {
        candidates.add(executablePath);
      }
    }
    // 没有环境检测结果时(如命令行入口), 按优先级在PATH中查找
    for (String name : CPP_COMPILER_NAMES) {
      String found = findInPath(name);
      if (found != null) {
        candidates.add(found);
      }
    }
    for (String candidate : candidates) {
      File file = new File(candidate);
      if (file.isFile()) {
        String fileName = file.getName().toLowerCase(Locale.ROOT);
        boolean msvc = fileName.equals("cl") || fileName.equals("cl.exe");
        return new CppCompiler(file.getAbsolutePath(), msvc);
      }
    }
    return null;
  }

  /**
   * 构建加载MSVC开发环境(vcvars)的命令前缀
   *
   * @param compilerExecutable cl.exe路径
   * @return 如 call "xxx\\vcvars64.bat" >nul && 的前缀, 未找到返回null
   */
  private static String buildVcvarsCommandPrefix(String compilerExecutable) {
    File dir = new File(compilerExecutable).getParentFile();
    // cl.exe位于 <VS>\\VC\\Tools\\MSVC\\<ver>\\bin\\Host*\\*, 向上逐层查找vcvars脚本
    for (int i = 0; i < 8 && dir != null; i++) {
      File buildDir = new File(dir, "VC" + File.separator + "Auxiliary" + File.separator + "Build");
      File vcvars64 = new File(buildDir, "vcvars64.bat");
      if (vcvars64.isFile()) {
        return "call \"" + vcvars64.getAbsolutePath() + "\" >nul && ";
      }
      File vcvarsAll = new File(buildDir, "vcvarsall.bat");
      if (vcvarsAll.isFile()) {
        return "call \"" + vcvarsAll.getAbsolutePath() + "\" x64 >nul && ";
      }
      dir = dir.getParentFile();
    }
    return null;
  }

  /** 将命令列表拼接为命令行(含空格的参数加引号) */
  private static String toCommandLine(List<String> command) {
    StringBuilder commandLine = new StringBuilder();
    for (String token : command) {
      if (commandLine.length() > 0) {
        commandLine.append(' ');
      }
      boolean needQuote = token.indexOf(' ') >= 0 || token.indexOf('\t') >= 0;
      commandLine.append(needQuote ? "\"" + token + "\"" : token);
    }
    return commandLine.toString();
  }

  /** 在PATH中查找可执行文件(Win下兼容.exe后缀) */
  private static String findInPath(String name) {
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null || pathEnv.isEmpty()) {
      return null;
    }
    for (String dir : pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
      if (dir.isBlank()) {
        continue;
      }
      File candidate = new File(dir, name);
      if (candidate.isFile()) {
        return candidate.getAbsolutePath();
      }
      if (isWindows()) {
        String pathExt = System.getenv("PATHEXT");
        if (pathExt == null || pathExt.isEmpty()) {
          pathExt = ".COM;.EXE;.BAT;.CMD";
        }
        for (String ext : pathExt.split(";")) {
          File extCandidate = new File(dir, name + ext.toLowerCase(Locale.ROOT));
          if (extCandidate.isFile()) {
            return extCandidate.getAbsolutePath();
          }
        }
      }
    }
    return null;
  }

  /** 是否为Windows平台 */
  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
  }

  /** C++编译器信息 */
  private static class CppCompiler {
    private final String executable;
    private final boolean msvc;

    CppCompiler(String executable, boolean msvc) {
      this.executable = executable;
      this.msvc = msvc;
    }

    String getExecutable() {
      return executable;
    }

    boolean isMsvc() {
      return msvc;
    }
  }

  /** 组装 Java 编译/运行所需类路径(运行期classpath + templatelib/*.jar) */
  private static String buildJavaClasspath() {
    Set<String> entries = new LinkedHashSet<>();
    String runtimeClasspath = System.getProperty("java.class.path");
    if (runtimeClasspath != null && !runtimeClasspath.isBlank()) {
      for (String entry :
          runtimeClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
        if (!entry.isBlank()) {
          entries.add(entry);
        }
      }
    }

    File libDir =
        new File(
            FileUtils.getRootDir(), DefaultEnvConfigConstant.DIR_JAVA_LIB_HOT_RELOAD_DEPEND_ON);
    File[] jarFiles =
        libDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
    if (jarFiles != null && jarFiles.length > 0) {
      Arrays.sort(jarFiles, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
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
      return commands.stream()
          .map(cmd -> String.join(" ", cmd))
          .collect(Collectors.joining(" && "));
    }
  }
}
