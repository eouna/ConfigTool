package com.eouna.configtool.update;

import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.utils.FileUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * 版本检查: 从 github release 静默查询最新版本。
 *
 * <p>本类通过 {@code @Component} 注册到容器中统一管理, 使用时通过 {@code @AutoInject} 注入:
 *
 * <pre>
 * &#64;AutoInject private VersionChecker versionChecker;
 *
 * VersionCheckResult result = versionChecker.checkLatest();
 * </pre>
 *
 * <p>约定:
 *
 * <ul>
 *   <li>检查是静默的: 连接与读取都有 {@link DefaultEnvConfigConstant#VERSION_CHECK_TIMEOUT_MILLIS} 超时,
 *       失败/超时只记日志并返回失败结果, 不抛异常; 超时后本次不再重试, 下次启动再检查;
 *   <li>每个进程只做一次静默检查(通过 {@link #markSilentCheckStarted()}), 手动检查不受限制;
 *   <li>用户点击"跳过本次"的版本会被记录到 {@link DefaultEnvConfigConstant#VERSION_SKIP_RECORD_PATH},
 *       后续静默检查不再提示该版本。
 * </ul>
 *
 * <p>注意: 使用 {@link HttpURLConnection}(属于 {@code java.base}), 打包用的精简运行库需要包含 {@code jdk.crypto.ec}
 * 才能完成 HTTPS 握手。
 *
 * @author CCL
 */
@Component
public class VersionChecker {

  /** github release 接口中版本号字段 */
  private static final String FIELD_TAG_NAME = "tag_name";

  /** github release 接口中页面地址字段 */
  private static final String FIELD_HTML_URL = "html_url";

  /** github api 要求的 User-Agent */
  private static final String USER_AGENT = "ConfigTool-VersionChecker";

  /** 版本号中的数字段 */
  private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\\d+");

  /** 本次启动是否已经执行过静默检查 */
  private final AtomicBoolean silentCheckStarted = new AtomicBoolean(false);

  /** 已跳过提示的版本(懒加载, 只需要读一次文件) */
  private volatile Set<String> skippedVersions;

  public VersionChecker() {}

  /** 当前工具版本(形如 v1.0.9) */
  public String getCurrentVersion() {
    return FileUtils.getAppVersion();
  }

  /**
   * 标记静默检查开始(每个进程只执行一次静默检查)。
   *
   * @return true表示本次调用获得了执行静默检查的资格
   */
  public boolean markSilentCheckStarted() {
    return silentCheckStarted.compareAndSet(false, true);
  }

  /**
   * 查询github最新release并与当前版本比较。
   *
   * <p>不抛异常: 任何网络/解析问题都返回失败结果, 由调用方决定是否提示。
   *
   * @return 检查结果
   */
  public VersionCheckResult checkLatest() {
    String currentVersion = getCurrentVersion();
    HttpURLConnection connection = null;
    try {
      URL url = new URL(DefaultEnvConfigConstant.GITHUB_LATEST_RELEASE_API);
      connection = (HttpURLConnection) url.openConnection();
      // 静默检查: 连接与读取都限时, 超时直接放弃(下次启动再检查)
      connection.setConnectTimeout(DefaultEnvConfigConstant.VERSION_CHECK_TIMEOUT_MILLIS);
      connection.setReadTimeout(DefaultEnvConfigConstant.VERSION_CHECK_TIMEOUT_MILLIS);
      connection.setRequestMethod("GET");
      connection.setRequestProperty("User-Agent", USER_AGENT);
      connection.setRequestProperty("Accept", "application/vnd.github+json");
      int responseCode = connection.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        String message = "http " + responseCode;
        LoggerUtils.getLogger().info("检查更新失败: {}", message);
        return VersionCheckResult.failure(currentVersion, message, false);
      }
      String body = readResponse(connection.getInputStream());
      JsonObject release = JsonParser.parseString(body).getAsJsonObject();
      String latestVersion = getJsonString(release, FIELD_TAG_NAME);
      if (StringUtils.isEmpty(latestVersion)) {
        LoggerUtils.getLogger().info("检查更新失败: 响应中没有版本号");
        return VersionCheckResult.failure(currentVersion, "响应中没有版本号", false);
      }
      String releaseUrl = getJsonString(release, FIELD_HTML_URL);
      if (StringUtils.isEmpty(releaseUrl)) {
        releaseUrl = DefaultEnvConfigConstant.GITHUB_RELEASE_PAGE;
      }
      boolean newVersionFound = compareVersion(latestVersion, currentVersion) > 0;
      LoggerUtils.getLogger()
          .info(
              "检查更新完成, 当前版本: {}, 最新版本: {}, 是否有新版本: {}",
              currentVersion,
              latestVersion,
              newVersionFound);
      return VersionCheckResult.success(currentVersion, latestVersion, newVersionFound, releaseUrl);
    } catch (SocketTimeoutException timeoutException) {
      LoggerUtils.getLogger()
          .info(
              "检查更新超时(超过 {}ms), 本次跳过, 下次启动再检查",
              DefaultEnvConfigConstant.VERSION_CHECK_TIMEOUT_MILLIS);
      return VersionCheckResult.failure(currentVersion, "请求超时", true);
    } catch (Exception e) {
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      LoggerUtils.getLogger().info("检查更新失败: {}", message);
      return VersionCheckResult.failure(currentVersion, message, false);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * 版本号比较(只比较数字段: v1.0.11 > v1.0.9)。
   *
   * @param left 版本号
   * @param right 版本号
   * @return 大于0表示left更新, 小于0表示right更新, 0表示相同
   */
  public static int compareVersion(String left, String right) {
    List<Long> leftNumbers = splitVersionNumbers(left);
    List<Long> rightNumbers = splitVersionNumbers(right);
    int size = Math.max(leftNumbers.size(), rightNumbers.size());
    for (int i = 0; i < size; i++) {
      long leftNumber = i < leftNumbers.size() ? leftNumbers.get(i) : 0L;
      long rightNumber = i < rightNumbers.size() ? rightNumbers.get(i) : 0L;
      if (leftNumber != rightNumber) {
        return Long.compare(leftNumber, rightNumber);
      }
    }
    return 0;
  }

  /** 版本号是否为当前版本之后的新版本 */
  public boolean isNewerThanCurrent(String version) {
    return compareVersion(version, getCurrentVersion()) > 0;
  }

  /**
   * 指定版本是否已被用户跳过提示。
   *
   * @param version 版本号
   * @return 已跳过返回true
   */
  public boolean isSkipped(String version) {
    if (StringUtils.isEmpty(version)) {
      return false;
    }
    String target = version.trim();
    for (String skipped : getSkippedVersions()) {
      if (skipped.equalsIgnoreCase(target)) {
        return true;
      }
      // 兼容记录里带/不带 v 前缀的情况(如 v1.1.0 与 1.1.0)
      if (!splitVersionNumbers(skipped).isEmpty() && compareVersion(skipped, target) == 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * 记录用户"跳过本次"的版本, 后续静默检查不再提示该版本。
   *
   * @param version 版本号
   */
  public void skipVersion(String version) {
    if (StringUtils.isEmpty(version)) {
      return;
    }
    String normalized = version.trim();
    Set<String> skipped = new LinkedHashSet<>(getSkippedVersions());
    if (!skipped.add(normalized)) {
      return;
    }
    skippedVersions = skipped;
    try {
      File recordFile =
          new File(
              FileUtils.getRelatedPathOfRoot(DefaultEnvConfigConstant.VERSION_SKIP_RECORD_PATH));
      File parent = recordFile.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
        LoggerUtils.getLogger().error("创建版本跳过记录目录失败: {}", parent.getAbsolutePath());
        return;
      }
      Files.write(recordFile.toPath(), skipped, StandardCharsets.UTF_8);
      LoggerUtils.getLogger().info("已记录跳过更新的版本: {}", normalized);
    } catch (Exception e) {
      LoggerUtils.getLogger().error("写入版本跳过记录失败", e);
    }
  }

  /** 已跳过提示的版本(按记录顺序) */
  public Set<String> getSkippedVersions() {
    Set<String> cached = skippedVersions;
    if (cached != null) {
      return cached;
    }
    Set<String> loaded = new LinkedHashSet<>();
    try {
      File recordFile =
          new File(
              FileUtils.getRelatedPathOfRoot(DefaultEnvConfigConstant.VERSION_SKIP_RECORD_PATH));
      if (recordFile.isFile()) {
        for (String line : Files.readAllLines(recordFile.toPath(), StandardCharsets.UTF_8)) {
          String version = line == null ? "" : line.trim();
          if (!version.isEmpty()) {
            loaded.add(version);
          }
        }
      }
    } catch (Exception e) {
      LoggerUtils.getLogger().error("读取版本跳过记录失败", e);
    }
    skippedVersions = loaded;
    return loaded;
  }

  /** 读取http响应内容(utf-8) */
  private static String readResponse(InputStream inputStream) throws Exception {
    try (InputStream stream = inputStream;
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[4096];
      int length;
      while ((length = stream.read(buffer)) != -1) {
        output.write(buffer, 0, length);
      }
      return output.toString(StandardCharsets.UTF_8);
    }
  }

  /** 读取json中的字符串字段 */
  private static String getJsonString(JsonObject jsonObject, String fieldName) {
    if (jsonObject == null
        || !jsonObject.has(fieldName)
        || jsonObject.get(fieldName).isJsonNull()) {
      return null;
    }
    return jsonObject.get(fieldName).getAsString();
  }

  /** 提取版本号中的数字段(如 v1.0.11 -> [1, 0, 11]) */
  private static List<Long> splitVersionNumbers(String version) {
    List<Long> numbers = new ArrayList<>();
    if (StringUtils.isEmpty(version)) {
      return numbers;
    }
    Matcher matcher = VERSION_NUMBER_PATTERN.matcher(version);
    while (matcher.find()) {
      try {
        numbers.add(Long.parseLong(matcher.group()));
      } catch (NumberFormatException ignored) {
        // 数字过大时忽略该段, 继续解析后面的版本段
      }
    }
    return numbers;
  }
}
