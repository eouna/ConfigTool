package com.eouna.configtool.core.boot.env;

/**
 * 环境常量
 *
 * @author CCL
 */
public interface EnvironmentConstant {

  /** 以什么模式进行启动 gui or commandline */
  String COMMEND_LINE_ARG_START_MODE = "mode";

  /** 启动模式 */
  enum StartMode {
    // 带界面
    DEFAULT_GUI,
    // 命令行
    COMMAND_LINE;

    public static StartMode getModeByStr(String modeStr) {
      for (StartMode value : values()) {
        if (modeStr.equalsIgnoreCase(value.name())) {
          return value;
        }
      }
      return null;
    }
  }
}
