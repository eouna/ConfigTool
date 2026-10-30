package com.eouna.configtool.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 启动外部工具命令时的跨平台处理。
 *
 * <p>Windows 的 npm/tsc 等命令通常是 {@code .cmd} 脚本, ProcessBuilder 不能直接执行, 需要先交给
 * {@code cmd.exe /c}。这里统一处理, 避免环境探测和预加载使用两套规则。
 *
 * @author CCL
 */
public final class ProcessCommandUtils {

  private ProcessCommandUtils() {}

  /**
   * 按当前平台准备可直接交给 ProcessBuilder 的命令。
   *
   * @param command 原始命令
   * @return 处理后的新命令列表
   */
  public static List<String> prepare(List<String> command) {
    if (command == null || command.isEmpty()) {
      return new ArrayList<>();
    }
    if (!isWindows() || !requiresCmdWrapper(command.get(0))) {
      return new ArrayList<>(command);
    }
    return new ArrayList<>(
        Arrays.asList("cmd.exe", "/c", toCommandLine(command)));
  }

  /**
   * 按当前平台准备可直接交给 ProcessBuilder 的命令。
   *
   * @param command 原始命令
   * @return 处理后的新命令数组
   */
  public static String[] prepare(String[] command) {
    return prepare(Arrays.asList(command)).toArray(new String[0]);
  }

  /** 将命令参数拼接为 Windows 命令行文本。 */
  public static String toCommandLine(List<String> command) {
    StringBuilder commandLine = new StringBuilder();
    for (String token : command) {
      if (commandLine.length() > 0) {
        commandLine.append(' ');
      }
      boolean needQuote =
          token.indexOf(' ') >= 0 || token.indexOf('\t') >= 0 || token.indexOf('"') >= 0;
      commandLine.append(needQuote ? "\"" + token.replace("\"", "\\\"") + "\"" : token);
    }
    return commandLine.toString();
  }

  private static boolean requiresCmdWrapper(String executable) {
    if (isCmdScript(executable)) {
      return true;
    }
    String resolved = findInPath(executable);
    return resolved != null && isCmdScript(resolved);
  }

  private static boolean isCmdScript(String executable) {
    String lower = executable.toLowerCase(Locale.ROOT);
    return lower.endsWith(".cmd") || lower.endsWith(".bat");
  }

  private static String findInPath(String executable) {
    File executableFile = new File(executable);
    if (executableFile.isFile()) {
      return executableFile.getAbsolutePath();
    }
    String pathEnv = System.getenv("PATH");
    if (pathEnv == null || pathEnv.isEmpty()) {
      return null;
    }
    String pathExt = System.getenv("PATHEXT");
    if (pathExt == null || pathExt.isEmpty()) {
      pathExt = ".COM;.EXE;.BAT;.CMD";
    }
    for (String dir : pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
      if (dir.isBlank()) {
        continue;
      }
      for (String ext : pathExt.split(";")) {
        File candidate = new File(dir, executable + ext.toLowerCase(Locale.ROOT));
        if (candidate.isFile()) {
          return candidate.getAbsolutePath();
        }
      }
    }
    return null;
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
  }
}
