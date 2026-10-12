package com.eouna.configtool.env;

/**
 * 单个语言模板的运行环境检测结果。
 *
 * @author CCL
 */
public class TemplateEnvResult {

  /** 语言标识(与 --lang 一致, 如 java/go/js) */
  private final String langId;
  /** 语言名称(用于展示) */
  private final String langName;
  /** 是否需要运行环境(json这类纯数据模板不需要) */
  private final boolean envRequired;
  /** 环境是否可用 */
  private final boolean available;
  /** 检测使用的命令 */
  private final String command;
  /** 可执行文件路径 */
  private final String executablePath;
  /** 版本信息(取命令输出的第一行) */
  private final String version;
  /** 说明信息 */
  private final String message;

  public TemplateEnvResult(
      String langId,
      String langName,
      boolean envRequired,
      boolean available,
      String command,
      String executablePath,
      String version,
      String message) {
    this.langId = langId;
    this.langName = langName;
    this.envRequired = envRequired;
    this.available = available;
    this.command = command;
    this.executablePath = executablePath;
    this.version = version;
    this.message = message;
  }

  public String getLangId() {
    return langId;
  }

  public String getLangName() {
    return langName;
  }

  public boolean isEnvRequired() {
    return envRequired;
  }

  public boolean isAvailable() {
    return available;
  }

  public String getCommand() {
    return command;
  }

  public String getExecutablePath() {
    return executablePath;
  }

  public String getVersion() {
    return version;
  }

  public String getMessage() {
    return message;
  }

  /** 简要描述, 用于日志输出 */
  public String toShortString() {
    if (!envRequired) {
      return langId + "=无需环境";
    }
    if (available) {
      return langId + "=可用(" + (version == null || version.isEmpty() ? executablePath : version) + ")";
    }
    return langId + "=不可用(未找到 " + command + ")";
  }

  @Override
  public String toString() {
    return "TemplateEnvResult{"
        + "langId='"
        + langId
        + '\''
        + ", envRequired="
        + envRequired
        + ", available="
        + available
        + ", command='"
        + command
        + '\''
        + ", executablePath='"
        + executablePath
        + '\''
        + ", version='"
        + version
        + '\''
        + ", message='"
        + message
        + '\''
        + '}';
  }
}