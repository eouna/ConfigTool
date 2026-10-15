package com.eouna.configtool.update;

/**
 * 版本检查结果。
 *
 * <p>由 {@link VersionChecker} 返回: 检查成功时带上远端最新版本与release地址, 失败(含超时)时带上失败原因。
 *
 * @author CCL
 */
public class VersionCheckResult {

  /** 检查是否成功(网络请求与解析都正常) */
  private final boolean success;

  /** 当前版本(形如 v1.0.9) */
  private final String currentVersion;

  /** 远端最新版本(tag名, 形如 v1.1.0; 失败时为null) */
  private final String latestVersion;

  /** 是否存在比当前更新的版本 */
  private final boolean newVersionFound;

  /** release页面地址(点击更新时跳转) */
  private final String releaseUrl;

  /** 失败原因(成功时为null) */
  private final String errorMessage;

  /** 是否由于超时导致失败 */
  private final boolean timeout;

  private VersionCheckResult(
      boolean success,
      String currentVersion,
      String latestVersion,
      boolean newVersionFound,
      String releaseUrl,
      String errorMessage,
      boolean timeout) {
    this.success = success;
    this.currentVersion = currentVersion;
    this.latestVersion = latestVersion;
    this.newVersionFound = newVersionFound;
    this.releaseUrl = releaseUrl;
    this.errorMessage = errorMessage;
    this.timeout = timeout;
  }

  /**
   * 检查成功
   *
   * @param currentVersion 当前版本
   * @param latestVersion 远端最新版本
   * @param newVersionFound 是否有更新
   * @param releaseUrl release页面地址
   * @return 检查结果
   */
  public static VersionCheckResult success(
      String currentVersion, String latestVersion, boolean newVersionFound, String releaseUrl) {
    return new VersionCheckResult(
        true, currentVersion, latestVersion, newVersionFound, releaseUrl, null, false);
  }

  /**
   * 检查失败
   *
   * @param currentVersion 当前版本
   * @param errorMessage 失败原因
   * @param timeout 是否由于超时失败
   * @return 检查结果
   */
  public static VersionCheckResult failure(
      String currentVersion, String errorMessage, boolean timeout) {
    return new VersionCheckResult(false, currentVersion, null, false, null, errorMessage, timeout);
  }

  public boolean isSuccess() {
    return success;
  }

  public String getCurrentVersion() {
    return currentVersion;
  }

  public String getLatestVersion() {
    return latestVersion;
  }

  public boolean isNewVersionFound() {
    return newVersionFound;
  }

  public String getReleaseUrl() {
    return releaseUrl;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public boolean isTimeout() {
    return timeout;
  }

  /** 失败原因描述(用于日志与弹窗) */
  public String getErrorDescription() {
    if (success) {
      return "";
    }
    if (timeout) {
      return "请求超时";
    }
    return errorMessage == null || errorMessage.isEmpty() ? "未知错误" : errorMessage;
  }
}
