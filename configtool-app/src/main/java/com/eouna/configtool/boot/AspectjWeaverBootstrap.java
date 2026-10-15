package com.eouna.configtool.boot;

import com.eouna.configtool.core.logger.LoggerUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * AspectJ 载入期织入(LTW)引导。
 *
 * <p>项目里的配置回存依赖 {@code ConfigDataUpdateAspect}。maven 构建会用 ajc 织入该切面, 但用 IDEA(或任何纯 javac)
 * 编译运行时不会织入, 于是界面上改配置就不会回存到 ToolConfig.yaml。
 *
 * <p>这里在启动最早期检测织入器是否已装载; 未装载时自动把 {@code aspectjweaver} 作为 java agent 挂到当前JVM上
 * (通过子进程 attach, 规避JDK不允许自attach的限制), 这样无需修改IDEA运行配置、也无需手写 {@code -javaagent}。
 *
 * <p>织入器jar查找顺序:
 *
 * <ol>
 *   <li>系统属性 {@code configtool.aspectjweaver.path};
 *   <li>运行目录下的 {@code templatelib/aspectjweaver*.jar}(发布包内位置);
 *   <li>类路径中包含 {@code aspectjweaver*.jar} 的条目(IDEA依赖目录);
 *   <li>运行目录下的 {@code aspectjweaver*.jar}。
 * </ol>
 *
 * <p>注意: 打包用的精简运行库需要包含 {@code jdk.attach} 模块, attach子进程才能工作。
 *
 * @author CCL
 */
public final class AspectjWeaverBootstrap {

  /** 织入器agent类(由aspectjweaver提供) */
  private static final String WEAVER_AGENT_CLASS = "org.aspectj.weaver.loadtime.Agent";

  /** attach辅助进程的主类 */
  private static final String ATTACHER_CLASS = "com.eouna.configtool.boot.AspectjAgentAttacher";

  /** 指定织入器jar的系统属性 */
  private static final String WEAVER_PATH_PROPERTY = "configtool.aspectjweaver.path";

  /** 织入器jar名匹配 */
  private static final Pattern WEAVER_JAR_NAME_PATTERN =
      Pattern.compile("aspectjweaver.*\\.jar", Pattern.CASE_INSENSITIVE);

  /** attach超时时间(秒) */
  private static final long ATTACH_TIMEOUT_SECONDS = 30L;

  /** 向上查找目录的最大层数 */
  private static final int MAX_SEARCH_LEVEL = 5;

  /** 构建期织入标记: ajc织入后的class常量池会包含切面类名 */
  private static final byte[] WEAVE_MARKER =
      "ConfigDataUpdateAspect".getBytes(StandardCharsets.UTF_8);

  /** 是否已经尝试过装载(只尝试一次) */
  private static final AtomicBoolean LOAD_ATTEMPTED = new AtomicBoolean(false);

  /** 织入状态是否已经打过日志(启动入口会调用两次, 避免重复打印) */
  private static final AtomicBoolean STATUS_LOGGED = new AtomicBoolean(false);

  private AspectjWeaverBootstrap() {}

  /**
   * 确保AspectJ织入器已装载(幂等)。
   *
   * <p>已通过 {@code -javaagent} 或构建期织入生效时不会重复attach; 找不到织入器jar时只记日志, 不影响程序启动。
   */
  public static void ensureAspectjWeaverLoaded() {
    if (isWeaverLoaded()) {
      if (STATUS_LOGGED.compareAndSet(false, true)) {
        LoggerUtils.getLogger().info("AspectJ织入已生效(配置变更会自动回存)");
      }
      return;
    }
    if (isBuildTimeWoven()) {
      // maven构建已用ajc织入(maven package / 发布包), 不需要再attach
      if (STATUS_LOGGED.compareAndSet(false, true)) {
        LoggerUtils.getLogger().info("检测到构建期已织入AspectJ切面, 配置变更会自动回存");
      }
      return;
    }
    if (!LOAD_ATTEMPTED.compareAndSet(false, true)) {
      return;
    }
    try {
      File weaverJar = findWeaverJar();
      if (weaverJar == null) {
        LoggerUtils.getLogger()
            .warn("未找到aspectjweaver.jar, 跳过自动织入(配置变更可能不会回存); 可在templatelib下放置该jar");
        return;
      }
      boolean attachSuccess = attachWeaver(weaverJar);
      if (attachSuccess && isWeaverLoaded()) {
        LoggerUtils.getLogger().info("已自动启用AspectJ织入(agent: {}), 配置变更会自动回存", weaverJar.getName());
      } else {
        LoggerUtils.getLogger()
            .warn(
                "自动启用AspectJ织入失败(agent: {}), 配置变更可能不会回存; 可用 -javaagent:{} 启动",
                weaverJar.getAbsolutePath(),
                weaverJar.getAbsolutePath());
      }
    } catch (Throwable throwable) {
      LoggerUtils.getLogger().error("自动启用AspectJ织入异常", throwable);
    }
  }

  /** 织入器是否已经装载(反射检查, 未装载时返回false) */
  public static boolean isWeaverLoaded() {
    try {
      Class<?> agentClass =
          Class.forName(WEAVER_AGENT_CLASS, false, AspectjWeaverBootstrap.class.getClassLoader());
      Object instrumentation = agentClass.getMethod("getInstrumentation").invoke(null);
      return instrumentation != null;
    } catch (Throwable ignored) {
      return false;
    }
  }

  /**
   * 判断配置bean是否已经在构建期被ajc织入过。
   *
   * <p>已织入的class常量池里会引用切面类(ConfigDataUpdateAspect); 纯javac(IDEA)编译的class没有该引用。
   * 这里只读取class字节, 不会触发类加载。
   *
   * @return true表示构建期已织入
   */
  private static boolean isBuildTimeWoven() {
    String classResource = "/com/eouna/configtool/configholder/ConfigDataBean$ExcelConf.class";
    try (InputStream inputStream = AspectjWeaverBootstrap.class.getResourceAsStream(classResource)) {
      if (inputStream == null) {
        return false;
      }
      byte[] classBytes = inputStream.readAllBytes();
      return indexOf(classBytes, WEAVE_MARKER) >= 0;
    } catch (Exception e) {
      return false;
    }
  }

  /** 在字节数组中查找子串 */
  private static int indexOf(byte[] source, byte[] target) {
    if (source == null || target == null || target.length == 0 || source.length < target.length) {
      return -1;
    }
    outer:
    for (int start = 0; start <= source.length - target.length; start++) {
      for (int offset = 0; offset < target.length; offset++) {
        if (source[start + offset] != target[offset]) {
          continue outer;
        }
      }
      return start;
    }
    return -1;
  }

  /**
   * 查找aspectjweaver jar。
   *
   * <p>依次尝试: 系统属性 -> 类路径中的同名jar -> 以"运行目录"和"自身class/ jar所在目录"为起点向上若干级查找
   * {@code templatelib/aspectjweaver*.jar} 或同目录下的 {@code aspectjweaver*.jar}。
   */
  private static File findWeaverJar() {
    String configuredPath = System.getProperty(WEAVER_PATH_PROPERTY);
    if (configuredPath != null && !configuredPath.trim().isEmpty()) {
      File configuredFile = new File(configuredPath.trim());
      if (configuredFile.isFile()) {
        return configuredFile;
      }
    }
    // 1. 类路径中的aspectjweaver(IDEA依赖目录)
    String classpath = System.getProperty("java.class.path", "");
    for (String entry : classpath.split(Pattern.quote(File.pathSeparator))) {
      if (entry == null || entry.trim().isEmpty()) {
        continue;
      }
      File entryFile = new File(entry.trim());
      if (entryFile.isFile() && WEAVER_JAR_NAME_PATTERN.matcher(entryFile.getName()).matches()) {
        return entryFile;
      }
    }
    // 2. 以运行目录/自身class(或jar)所在目录为起点, 向上查找templatelib
    List<File> searchRoots = new ArrayList<>();
    searchRoots.add(currentDirectory());
    searchRoots.add(codeSourceDirectory());
    for (File root : searchRoots) {
      File current = root;
      for (int level = 0; current != null && level < MAX_SEARCH_LEVEL; level++) {
        File inTemplatelib = findWeaverJarInDir(new File(current, "templatelib"));
        if (inTemplatelib != null) {
          return inTemplatelib;
        }
        File inCurrent = findWeaverJarInDir(current);
        if (inCurrent != null) {
          return inCurrent;
        }
        current = current.getParentFile();
      }
    }
    return null;
  }

  /** 当前工作目录 */
  private static File currentDirectory() {
    return new File(System.getProperty("user.dir", "."));
  }

  /** 本类所在的位置(classes目录或jar所在目录) */
  private static File codeSourceDirectory() {
    try {
      File codeSource =
          new File(
              AspectjWeaverBootstrap.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI());
      return codeSource.isFile() ? codeSource.getParentFile() : codeSource;
    } catch (Exception e) {
      return currentDirectory();
    }
  }

  /** 在指定目录中查找aspectjweaver jar */
  private static File findWeaverJarInDir(File dir) {
    if (dir == null || !dir.isDirectory()) {
      return null;
    }
    File[] files = dir.listFiles();
    if (files == null) {
      return null;
    }
    for (File file : files) {
      if (file.isFile() && WEAVER_JAR_NAME_PATTERN.matcher(file.getName()).matches()) {
        return file;
      }
    }
    return null;
  }

  /** 通过子进程把织入器attach到当前JVM */
  private static boolean attachWeaver(File weaverJar) throws Exception {
    String javaExecutable = resolveJavaExecutable();
    String pid = String.valueOf(ProcessHandle.current().pid());
    List<String> command = new ArrayList<>();
    command.add(javaExecutable);
    command.add("-cp");
    command.add(System.getProperty("java.class.path", ""));
    command.add(ATTACHER_CLASS);
    command.add(pid);
    command.add(weaverJar.getAbsolutePath());
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    // 异步读取子进程输出, 避免管道写满阻塞
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Thread readerThread =
        new Thread(
            () -> {
              try (InputStream inputStream = process.getInputStream()) {
                inputStream.transferTo(outputStream);
              } catch (Exception ignored) {
                // 子进程结束时读取中断, 忽略
              }
            },
            "aspectj-attacher-output");
    readerThread.setDaemon(true);
    readerThread.start();
    if (!process.waitFor(ATTACH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      LoggerUtils.getLogger().warn("attach aspectjweaver 超时({}s)", ATTACH_TIMEOUT_SECONDS);
      return false;
    }
    readerThread.join(1000);
    String output = outputStream.toString(StandardCharsets.UTF_8).trim();
    boolean success = process.exitValue() == 0;
    if (!success) {
      LoggerUtils.getLogger()
          .warn("attach aspectjweaver 失败(exit={}): {}", process.exitValue(), output);
    } else {
      LoggerUtils.getLogger().info("attach aspectjweaver 输出: {}", output);
    }
    return success;
  }

  /** 获取当前JVM使用的java可执行文件 */
  private static String resolveJavaExecutable() {
    String command = ProcessHandle.current().info().command().orElse(null);
    if (command != null && !command.trim().isEmpty()) {
      return command;
    }
    String executableName =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe"
            : "java";
    return new File(new File(System.getProperty("java.home"), "bin"), executableName).getPath();
  }
}
