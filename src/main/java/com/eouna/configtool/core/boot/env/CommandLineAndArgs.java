package com.eouna.configtool.core.boot.env;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author CCL
 * @date 2023/7/10
 */
public class CommandLineAndArgs {

  /** 参数选项 */
  private final Map<String, List<String>> commandOptions = new HashMap<>();

  /** 单个选项列表 */
  private final List<String> singleOptions = new ArrayList<>();

  public CommandLineAndArgs(String... args) {
    // 解析command
    parseCommandLine(args);
  }

  /**
   * 解析参数
   *
   * @param args 参数
   */
  private void parseCommandLine(String[] args) {
    if (args == null || args.length == 0) {
      return;
    }
    for (String arg : args) {
      if (arg.startsWith("--")) {
        if (arg.contains("=")) {
          String optionName = arg.substring(2, arg.indexOf("="));
          String optionValue = arg.substring(arg.indexOf("=") + 1);
          commandOptions.computeIfAbsent(optionName, k -> new ArrayList<>()).add(optionValue);
        } else {
          singleOptions.add(arg.substring(2));
        }
      } else {
        singleOptions.add(arg.substring(1));
      }
    }
  }

  public List<String> getOptionArg(String optionName) {
    return commandOptions.getOrDefault(optionName, new ArrayList<>());
  }

  public boolean containOptionName(String optionName) {
    if (commandOptions.containsKey(optionName)) {
      return true;
    }
    return singleOptions.contains(optionName);
  }
}
