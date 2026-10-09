package com.eouna.configtool.core.boot.env;

/**
 * 环境常量
 *
 * @author CCL
 */
public interface EnvironmentConstant {

  /** 以什么模式进行启动 gui or commandline */
  String COMMEND_LINE_ARG_START_MODE = "mode";

  /** 打印用法说明(命令行帮助) */
  String COMMAND_LINE_ARG_HELP = "help";

  /** 启动模式 */
  enum StartMode {
    // 带界面
    DEFAULT_GUI,
    // 命令行
    COMMAND_LINE;

    /**
     * 根据启动参数解析启动模式。
     *
     * <p>除枚举名外, 额外兼容 gui / none_gui / nogui / commandline / cli 等写法。
     *
     * @param modeStr 启动模式字符串
     * @return 对应的启动模式, 无法识别时返回null
     */
    public static StartMode getModeByStr(String modeStr) {
      if (modeStr == null) {
        return null;
      }
      String normalized = modeStr.trim().replace('-', '_').toLowerCase();
      for (StartMode value : values()) {
        if (value.name().equalsIgnoreCase(normalized)) {
          return value;
        }
      }
      return switch (normalized) {
        case "gui", "default", "fx" -> DEFAULT_GUI;
        case "none_gui", "nogui", "commandline", "command_line", "cli", "console" -> COMMAND_LINE;
        default -> null;
      };
    }
  }
}
