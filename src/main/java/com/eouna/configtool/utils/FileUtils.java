package com.eouna.configtool.utils;

import com.eouna.configtool.ConfigToolGenApplication;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.utils.ClassUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 文件助手
 *
 * @author CCL
 * @date 2023/3/2
 */
public class FileUtils {

  /** 文件夹过滤器 */
  public static FileFilter DEFAULT_DIR_FILTER =
      pathname -> pathname.exists() && pathname.isDirectory();

  /** excel文件过滤器 */
  public static final FileFilter EXCEL_FILTER =
      pathname -> {
        if (pathname.isDirectory()) {
          return true;
        }
        String fileName = pathname.getName();
        int latestDotIdx = fileName.lastIndexOf(".");
        if (latestDotIdx < 0) {
          return false;
        }
        String fileSuffix = fileName.substring(latestDotIdx + 1);
        String xlsxExtName = "xlsx", xlsExtName = "xls";
        boolean fileSuffixJudge = fileSuffix.equals(xlsxExtName) || fileSuffix.equals(xlsExtName);
        return !fileName.startsWith("~") && fileSuffixJudge;
      };

  /**
   * 获取资源路径
   *
   * @return 资源路径
   */
  public static String getFullResourceUrl(String resourcePath) {
    return Objects.requireNonNull(ConfigToolGenApplication.class.getResource(resourcePath))
        .toExternalForm();
  }

  /**
   * 获取资源路径
   *
   * @return 资源路径
   */
  public static String getFullResourcePath(String resourcePath) {
    return Objects.requireNonNull(ConfigToolGenApplication.class.getResource(resourcePath))
        .getPath();
  }

  /**
   * 是否是相对路径
   *
   * @param path 路径
   * @return 是否是相对
   */
  public static boolean isAbsolutePath(String path) {
    // 相对路径
    return path.startsWith("/") || path.indexOf(":") > 0;
  }

  /**
   * 列出文件夹下所有的excel文件
   *
   * @param excelFile excel文件夹
   * @return 文件
   */
  public static Map<String, File> listExcelFile(File excelFile) {
    return listFiles(excelFile, EXCEL_FILTER);
  }

  /**
   * 列出文件夹下所有的文件
   *
   * @param dirFile 文件夹
   * @param fileFilter 文件过滤器
   * @return 文件
   */
  public static Map<String, File> listFiles(File dirFile, FileFilter fileFilter) {
    Map<String, File> allFileMap = new HashMap<>(8);
    if (!dirFile.isDirectory()) {
      return allFileMap;
    }
    ArrayList<File> fileList =
        new ArrayList<>(Arrays.asList(Objects.requireNonNull(dirFile.listFiles())));
    for (File file : fileList) {
      if (file.isDirectory()) {
        allFileMap.putAll(listFiles(file, fileFilter));
      } else if (fileFilter == null || fileFilter.accept(file)) {
        allFileMap.put(file.getName(), file);
      }
    }
    return allFileMap;
  }

  /**
   * 获取PID
   *
   * @return pid
   */
  public static int getPid() {
    return Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
  }

  /** 获取根路径 */
  public static String getRootDir() {
    String rootDir = System.getProperty("user.dir");
    if (!StrUtils.isEmpty(rootDir)) {
      return rootDir + File.separator;
    }
    String mainClassPackageName = ConfigToolGenApplication.class.getPackage().getName();
    String mainClassPackagePath = mainClassPackageName.replace(".", File.separator);
    String mainClassPath =
        Objects.requireNonNull(ConfigToolGenApplication.class.getResource("")).getPath();
    return mainClassPath.substring(
        0, mainClassPath.indexOf(File.separator + mainClassPackagePath) + 1);
  }

  /**
   * 获取class对应的根路径
   *
   * @param aClass class
   * @return 根路径
   */
  public static String getClassRootDir(Class<?> aClass) {
    String rootDir = System.getProperty("user.dir");
    if (!StrUtils.isEmpty(rootDir)) {
      return rootDir + File.separator;
    }
    return ClassUtils.getClassResourcePath(aClass);
  }

  /**
   * 通过相对路径获取绝对路径
   *
   * @param relatedPath 相对路径
   * @return 绝对路径
   */
  public static String getRelatedPathOfRoot(String relatedPath) {
    return getRootDir() + relatedPath;
  }

  /**
   * 获取或者创建文件目录
   *
   * @param dirPath 目标文件夹路径
   * @return file
   * @throws FileSystemException e
   */
  public static File getOrCreateDir(String dirPath) throws FileSystemException {
    File basePathFile = new File(dirPath);
    if (!(basePathFile.exists())) {
      if (!basePathFile.mkdirs()) {
        throw new FileSystemException("文件夹创建失败 path: " + dirPath);
      }
    } else if (!basePathFile.isDirectory()) {
      throw new FileSystemException("文件路径是一个文件非文件夹 path: " + dirPath);
    }
    return basePathFile;
  }

  /**
   * 检测目标路径是否是相对路径,然后获取绝对路径
   *
   * @return 绝对路径
   */
  public static String getAbsolutePath(String path) {
    String pathCopy = new String(Arrays.copyOf(path.toCharArray(), path.length()));
    // 如果是相对路径则获取全路径
    if (!FileUtils.isAbsolutePath(pathCopy)) {
      pathCopy = FileUtils.getRelatedPathOfRoot(pathCopy);
    }
    return pathCopy;
  }

  /**
   * 获取当前maven版本号
   *
   * @return 版本号
   */
  public static String getAppVersion() {
    try {
      // 读取当前版本的maven版本
      MavenXpp3Reader reader = new MavenXpp3Reader();
      File file = new File("pom.xml");
      if (!file.exists()) {
        file = new File(getRootDir() + File.separator + "config/pom.xml");
        if (!file.exists()) {
          return DefaultEnvConfigConstant.TOOL_DEFAULT_VERSION;
        }
      }
      Model mavenModel = reader.read(new FileReader(file));
      return "v" + mavenModel.getVersion().split("-")[0];
    } catch (XmlPullParserException e) {
      e.printStackTrace();
      LoggerUtils.getLogger().error("读取maven文件失败", e);
    } catch (Exception e) {
      LoggerUtils.getLogger().error("读取POM文件异常", e);
    }
    return DefaultEnvConfigConstant.TOOL_DEFAULT_VERSION;
  }

  /**
   * 获取dir目录下的文件和文件的md5码
   *
   * @param dir 文件
   * @param fileFilter 文件过滤器
   * @return 文件及文件的MD5码
   */
  public static Map<File, String> getDirMd5CodeList(File dir, FileFilter fileFilter) {
    Map<String, File> files = listFiles(dir, fileFilter);
    return files.entrySet().stream()
        .map(
            entry -> {
              Map<File, String> dataMap = new HashMap<>(1);
              try {
                dataMap.put(
                    entry.getValue(),
                    DigestUtils.md5Hex(Files.newInputStream(entry.getValue().toPath())));
              } catch (IOException e) {
                LoggerUtils.getLogger().error("获取文件: " + entry.getKey() + " MD5码失败");
              }
              return dataMap;
            })
        .collect(HashMap::new, HashMap::putAll, HashMap::putAll);
  }

  /**
   * 执行系统命令
   *
   * @param command 命令
   * @return 结果
   */
  public static List<String> execSysCommand(String command) {
    try {
      Process process = Runtime.getRuntime().exec(command);
      InputStream in = process.getInputStream();
      BufferedReader inr = new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()));
      String line = inr.readLine();
      List<String> lines = new ArrayList<>();
      while (line != null) {
        line = line.toLowerCase(Locale.ENGLISH).trim();
        lines.add(line);
        line = inr.readLine();
      }
      in.close();
      inr.close();
      return lines;
    } catch (IOException e) {
      LoggerUtils.getLogger().error("执行命令: {}, 出错", command, e);
    }
    return new ArrayList<>();
  }

  /**
   * 生成运行PID
   *
   * @throws Exception e
   */
  public static void genServerPid() throws Exception {
    // 保存进程Id
    String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    try (FileWriter writer = new FileWriter("server.pid")) {
      writer.write(pid);
      writer.flush();
    }
  }
}
