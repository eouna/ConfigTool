package com.eouna.configtool.utils;

import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.core.logger.TextAreaStepLogger;
import javafx.scene.text.TextFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject.Kind;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 热加载java文件
 *
 * @author CCL
 */
public class HotClassLoaderUtils {

  /** logger */
  private static final Logger logger = LoggerFactory.getLogger(HotClassLoaderUtils.class);

  /**
   * java热加载
   *
   * @param javaFilePath java文件路径
   * @param javaClassPath java class存储路径
   * @param javaDependencyLibPath java依赖的jar或者java文件路径
   * @param packageName 运行文件夹对应的的基础包名
   * @param findClassName 需要运行的类名
   * @param runMethodName 需要运行的方法名
   * @param args 方法运行所需的字段
   * @throws IOException e
   * @throws ClassNotFoundException e
   * @throws NoSuchMethodException e
   * @throws InvocationTargetException e
   * @throws IllegalAccessException e
   */
  public static synchronized void loadClassAndRun(
      TextFlow logShowArea,
      String javaFilePath,
      String javaClassPath,
      String javaDependencyLibPath,
      String packageName,
      String findClassName,
      String runMethodName,
      MethodArgDataTuple<?>... args)
      throws Exception {
    TextAreaStepLogger textAreaStepLogger = new TextAreaStepLogger(logShowArea);
    textAreaStepLogger.info("开始热加载生成的配置表java文件");
    // java生成后的class路径
    File javaClassPathDir = checkDir(javaClassPath, false);
    if (!javaFilePath.equals(javaClassPath)) {
      // 清空class文件夹中的文件
      org.apache.commons.io.FileUtils.cleanDirectory(javaClassPathDir);
    }
    // 构建java编译任务
    DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
    CompilationTask task =
        buildJavaCompilerTask(
            javaFilePath, javaClassPath, javaDependencyLibPath, diagnosticCollector);

    long startTime = System.currentTimeMillis();
    textAreaStepLogger.info("开始编译java文件");
    // 编译文件
    boolean callRes = task.call();
    textAreaStepLogger.info("编译java文件结束, 共耗时: " + (System.currentTimeMillis() - startTime) + "ms");
    if (callRes) {
      // 获取加载类
      HotClassLoader classLoader = new HotClassLoader(javaClassPathDir.getPath());
      Class<?> aClass = classLoader.loadClass(packageName + "." + findClassName);
      if (aClass == null) {
        return;
      }
      // 获取实例
      Object instance = aClass.getDeclaredConstructor().newInstance();
      textAreaStepLogger.info("加载类: " + findClassName + " 成功");
      startTime = System.currentTimeMillis();
      if (args.length > 0) {
        List<Class<?>> classTypeList =
            Arrays.stream(args).map(MethodArgDataTuple::getClassType).collect(Collectors.toList());
        Class<?>[] classes = new Class[classTypeList.size()];
        for (int i = 0; i < classTypeList.size(); i++) {
          classes[i] = classTypeList.get(i);
        }
        Method method = aClass.getMethod(runMethodName, classes);
        textAreaStepLogger.info("开始运行类: " + findClassName + " 的方法: " + runMethodName);
        try {
          method.setAccessible(true);
          // 调用目标方法
          method.invoke(instance, Stream.of(args).map(MethodArgDataTuple::getData).toArray());
          logWhenCallMethodFinished(
              logShowArea, instance, runMethodName, startTime, javaClassPathDir, packageName);
        } catch (InvocationTargetException invocationTargetException) {
          throw new Exception(invocationTargetException.getTargetException());
        }
      } else {
        Method method = aClass.getMethod(runMethodName);
        method.invoke(instance);
        logWhenCallMethodFinished(
            logShowArea, instance, runMethodName, startTime, javaClassPathDir, packageName);
      }
    } else {
      StringBuilder error = new StringBuilder();
      for (Diagnostic<?> diagnostic : diagnosticCollector.getDiagnostics()) {
        error.append(compilePrint(diagnostic));
      }
      throw new Exception(error.toString());
    }
  }

  /**
   * 方法运行结束时打印
   *
   * @param instance 实例
   * @param runMethodName 方法名
   * @param startTime 开始运行时间
   */
  private static void logWhenCallMethodFinished(
      TextFlow textFlow,
      Object instance,
      String runMethodName,
      long startTime,
      File javaClassPathDir,
      String packageName) {
    TextAreaLogger textAreaLogger = new TextAreaLogger(textFlow);
    try {
      Object instanceRef = instance.getClass().getMethod("getInstance").invoke(null);
      textAreaLogger.info(
          "运行类: "
              + instance.getClass().getSimpleName()
              + " 的方法: "
              + runMethodName
              + " 结束,耗时: "
              + (System.currentTimeMillis() - startTime)
              + "ms"
          /*+ "文件大小: "
          + MemUsageUtils.humanSizeOf(
              instanceRef, (field) -> !field.getType().isAssignableFrom(Logger.class))*/ );
      // 删除class路径
      String classPath =
          javaClassPathDir.getPath()
              + File.separator
              + (packageName.contains(".")
                  ? packageName.substring(0, packageName.indexOf("."))
                  : packageName);
      org.apache.commons.io.FileUtils.deleteDirectory(new File(classPath));
    } catch (IOException e) {
      textAreaLogger.error("删除class热载文件夹失败", e);
    } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 构建java编译任务
   *
   * @param javaFilePath java文件路径
   * @param javaClassPath java class存储路径
   * @param javaDependencyLibPath java依赖的jar或者java文件路径
   * @param diagnosticCollector 诊断收集器
   * @return 编译任务
   * @throws IOException e
   */
  private static CompilationTask buildJavaCompilerTask(
      String javaFilePath,
      String javaClassPath,
      String javaDependencyLibPath,
      DiagnosticCollector<JavaFileObject> diagnosticCollector)
      throws IOException {
    // java文件路径
    File javaFilePathDir = checkDir(javaFilePath, true);
    // 查找java文件
    Map<String, File> javaFileMap =
        FileUtils.listFiles(
            javaFilePathDir,
            pathname ->
                pathname.isDirectory() || pathname.getName().endsWith(Kind.SOURCE.extension));
    // java依赖的jar或者java文件路径
    File javaDependencyLibPathDir = checkDir(javaDependencyLibPath, false);
    // 合并文件 class path
    String fileClassPath = getJavaClassPath(javaFilePathDir, javaDependencyLibPathDir);
    logger.info(fileClassPath);
    // 查找java文件和获取java文件内容
    List<JavaStringFileObject> javaStringFileObjects = getJavaStringFileObjectList(javaFileMap);
    // java编译器
    JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager standardJavaFileManager =
        javaCompiler.getStandardFileManager(
            diagnosticCollector, Locale.getDefault(), StandardCharsets.UTF_8);

    List<String> options = new ArrayList<>();
    options.add("-encoding");
    options.add("UTF-8");
    // -cp
    options.add("-classpath");
    options.add(fileClassPath);
    options.add("-d");
    options.add(javaClassPath);

    CompilationTask task =
        javaCompiler.getTask(
            null,
            standardJavaFileManager,
            diagnosticCollector,
            options,
            null,
            javaStringFileObjects);

    return task;
  }

  /** 错误打印 */
  private static String compilePrint(Diagnostic<?> diagnostic) {
    return "Code:["
        + diagnostic.getCode()
        + "]\n"
        + "Kind:["
        + diagnostic.getKind()
        + "]\n"
        + "Position:["
        + diagnostic.getPosition()
        + "]\n"
        + "Start Position:["
        + diagnostic.getStartPosition()
        + "]\n"
        + "End Position:["
        + diagnostic.getEndPosition()
        + "]\n"
        + "Source:["
        + diagnostic.getSource()
        + "]\n"
        + "LineNumber:["
        + diagnostic.getLineNumber()
        + "]\n"
        + "ColumnNumber:["
        + diagnostic.getColumnNumber()
        + "]\n"
        + "Message:["
        + diagnostic.getMessage(Locale.getDefault())
        + "]\n";
  }

  public static class MethodArgDataTuple<T> {
    /** 类名 */
    Class<T> className;

    /** 数据 */
    T data;

    public MethodArgDataTuple(Class<T> className, T data) {
      this.className = className;
      this.data = data;
    }

    public Class<T> getClassType() {
      return className;
    }

    public T getData() {
      return data;
    }
  }

  /**
   * 检查文件夹或者创建文件
   *
   * @param checkPath 检查文件夹路径
   * @param needCreate 是否创建
   * @return 文件
   * @throws IOException e
   */
  private static File checkDir(String checkPath, boolean needCreate) throws IOException {
    File checkPathDir = new File(checkPath);
    boolean isCorrectedPath = !checkPathDir.exists() || !checkPathDir.isDirectory();
    if (isCorrectedPath && !needCreate) {
      throw new IOException("未找到文件夹: " + checkPath);
    } else if (needCreate) {
      if (checkPathDir.mkdir()) {
        throw new IOException("创建文件失败: " + checkPath);
      }
    }
    return checkPathDir;
  }

  /**
   * 扫描java路径
   *
   * @param javaFilePathDir java文件map
   * @return 合成的class路径
   */
  private static String getJavaClassPath(File javaFilePathDir, File javaDependencyLibPathDir) {
    StringBuilder stringBuilder = new StringBuilder();
    Collector<CharSequence, ?, String> collector =
        System.getProperty("os.name").startsWith("Windows")
            ? Collectors.joining(";")
            : Collectors.joining(":");
    // 查找外部依赖文件
    Map<String, File> jarAndJavaDependencyFileMap =
        FileUtils.listFiles(
            javaDependencyLibPathDir,
            pathname ->
                pathname.isDirectory()
                    || pathname.getName().endsWith(Kind.SOURCE.extension)
                    || pathname.getName().endsWith(".jar"));
    String jarAndJavaDependencyPathListStr =
        jarAndJavaDependencyFileMap.values().stream().map(File::getAbsolutePath).collect(collector);
    stringBuilder.append(jarAndJavaDependencyPathListStr);
    // 查找java文件
    Map<String, File> javaFileMap =
        FileUtils.listFiles(
            javaFilePathDir,
            pathname ->
                pathname.isDirectory() || pathname.getName().endsWith(Kind.SOURCE.extension));
    String javaFilePathListStr =
        javaFileMap.values().stream().map(File::getAbsolutePath).collect(Collectors.joining(" "));
    stringBuilder.append(" ").append(javaFilePathListStr);
    return stringBuilder.toString();
  }

  /**
   * 扫描java文件
   *
   * @return java文件列表
   * @throws IOException e
   */
  private static List<JavaStringFileObject> getJavaStringFileObjectList(
      Map<String, File> javaFileMap) throws IOException {
    // 需要编译文件列表
    List<JavaStringFileObject> javaFileObjectList = new ArrayList<>();
    for (Entry<String, File> javaFile : javaFileMap.entrySet()) {
      String javaFileName = javaFile.getKey().replace(Kind.SOURCE.extension, "");
      StringBuilder javaContent = new StringBuilder();
      BufferedReader fileReader = new BufferedReader(new FileReader(javaFile.getValue()));
      String line;
      while ((line = fileReader.readLine()) != null) {
        javaContent.append(line).append("\n");
      }
      javaFileObjectList.add(new JavaStringFileObject(javaFileName, javaContent.toString()));
    }
    return javaFileObjectList;
  }

  public static class JavaStringFileObject extends SimpleJavaFileObject {

    private final String codeString;

    /**
     * Construct a SimpleJavaFileObject of the given kind and with the given URI.
     *
     * @param javaFileName java file name
     * @param codeString code content
     */
    protected JavaStringFileObject(String javaFileName, String codeString) {
      super(
          URI.create("string:///" + javaFileName.replace('.', '/') + Kind.SOURCE.extension),
          Kind.SOURCE);
      this.codeString = codeString;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return codeString;
    }
  }

  public static class HotClassLoader extends ClassLoader {

    private final String classPath;

    public HotClassLoader(String classPath) {
      this.classPath = classPath;
    }

    @Override
    protected Class<?> findClass(String className) throws ClassNotFoundException {
      try {
        String classPath =
            this.classPath
                + File.separator
                + className.replace(".", File.separator)
                + Kind.CLASS.extension;
        byte[] b = Files.readAllBytes(Paths.get(classPath));
        return defineClass(className, b, 0, b.length);
      } catch (IOException e) {
        logger.error("加载类失败", e);
      }
      return Thread.currentThread().getContextClassLoader().loadClass(className);
    }
  }
}
