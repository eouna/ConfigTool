package com.eouna.configtool.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 * 简易的版本检测工具,
 *
 * @author CCL
 * @date 2023/11/2
 */
public class VersionUtils {

  public enum UpdateState {
    // 需要强更
    FORCE_UPDATE("检测到大版本更新,请拉取最新程序"),
    // 功能更新
    FUNCTION_UPDATE("功能更新提示"),
    // 提示有版本更新,不强更
    BUG_FIX_UPDATE("bug修复更新提示"),
    // 版本检测相同,或其他情况
    NONE(""),
    ;

    /** 更新提示 */
    final String updateAlert;

    UpdateState(String updateAlert) {
      this.updateAlert = updateAlert;
    }

    public String getUpdateAlert() {
      return updateAlert;
    }
  }

  /** 更新结果 */
  public static class UpdateResult {
    // 版本状态
    private final UpdateState updateState;
    // 额外信息
    private String extInfo;
    private String curVersion;
    private String latestVersion;
    // 是否需要强制更新
    private final boolean needForceUpdate;

    public UpdateResult(UpdateState updateState) {
      this.updateState = updateState;
      this.needForceUpdate = false;
    }

    public UpdateResult(
        UpdateState updateState,
        String extInfo,
        String curVersion,
        String latestVersion,
        boolean needForceUpdate) {
      this.updateState = updateState;
      this.extInfo = extInfo;
      this.curVersion = curVersion;
      this.latestVersion = latestVersion;
      this.needForceUpdate = needForceUpdate;
    }

    public void setExtInfo(String extInfo) {
      this.extInfo = extInfo;
    }

    public String getCurVersion() {
      return curVersion;
    }

    public String getLatestVersion() {
      return latestVersion;
    }

    public UpdateState getUpdateState() {
      return updateState;
    }

    public String getExtInfo() {
      return extInfo;
    }

    public boolean isNeedForceUpdate() {
      return needForceUpdate;
    }
  }

  public static void checkVersion() {
    UpdateResult updateResult = VersionUtils.checkNeedUpdateVersion();
    UpdateState updateState = updateResult.getUpdateState();
    if (updateState == UpdateState.NONE) {
      return;
    }
    Alert alert = new Alert(updateResult.isNeedForceUpdate() ? AlertType.ERROR : AlertType.WARNING);
    String extInfo = updateResult.getExtInfo();
    String context =
        "当前版本: "
            + updateResult.getCurVersion()
            + "，"
            + "最新版本: v"
            + updateResult.getLatestVersion()
            + "\n"
            + (extInfo == null ? "" : "更新详情:\n" + extInfo);
    alert.setContentText(context);
    alert.setTitle("版本更新提示");
    alert.setHeaderText(updateState.getUpdateAlert());
    if (updateResult.isNeedForceUpdate()) {
      alert.setOnCloseRequest(event -> System.exit(15));
    }
    alert.showAndWait();
  }

  /**
   * 检查是否需要更新版本
   *
   * @return 版本更新检查
   */
  public static UpdateResult checkNeedUpdateVersion() {
    String appVersion = FileUtils.getAppVersion();
    VersionInfo versionInfo = new VersionInfo(appVersion);
    VersionInfo latestVersionInfo = getRemoteVersion();
    if (latestVersionInfo == null) {
      return new UpdateResult(UpdateState.NONE);
    }

    if (latestVersionInfo.primaryVersion > versionInfo.primaryVersion) {
      return new UpdateResult(
          UpdateState.FORCE_UPDATE,
          latestVersionInfo.updateInfo,
          appVersion,
          latestVersionInfo.fullVersion,
          true);
    }
    if (latestVersionInfo.functionVersion > versionInfo.functionVersion
        && latestVersionInfo.primaryVersion == versionInfo.primaryVersion) {
      return new UpdateResult(
          UpdateState.FUNCTION_UPDATE,
          latestVersionInfo.updateInfo,
          appVersion,
          latestVersionInfo.fullVersion,
          latestVersionInfo.needForceUpdate);
    }
    if (latestVersionInfo.bugFixVersion > versionInfo.bugFixVersion
        && latestVersionInfo.functionVersion == versionInfo.functionVersion) {
      return new UpdateResult(
          UpdateState.BUG_FIX_UPDATE,
          latestVersionInfo.updateInfo,
          appVersion,
          latestVersionInfo.fullVersion,
          false);
    }
    return new UpdateResult(UpdateState.NONE);
  }

  private static VersionInfo getRemoteVersion() {
    try {
      URL url = new URL("http://192.168.1.150:8888/ConfigTool/version");
      URLConnection urlConnection = url.openConnection();
      urlConnection.setDoOutput(true);
      InputStream o = urlConnection.getInputStream();
      InputStreamReader inputStreamReader = new InputStreamReader(o);
      BufferedReader reader = new BufferedReader(inputStreamReader);
      List<String> lineData = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        lineData.add(line);
      }
      reader.close();
      if (!lineData.isEmpty()) {
        return new VersionInfo(lineData);
      }
    } catch (IOException e) {
      throw new RuntimeException();
    }
    return null;
  }

  static class VersionInfo {
    /** 主版本,程序整体改动较大,不兼容时修改 */
    int primaryVersion;

    /** 功能修改版本, 新增功能时修改 */
    int functionVersion;

    /** bug修复版本, 版本有bug修复时修改 */
    int bugFixVersion;

    /** 更新信息 */
    String updateInfo;

    /** 完整版本 */
    String fullVersion;

    /** 是否需要强制更新 */
    boolean needForceUpdate;

    public VersionInfo(String versionStr) {
      if (!versionStr.startsWith("v")) {
        throw new RuntimeException();
      }
      versionStr = versionStr.substring(1);
      parseVersionInfo(versionStr);
    }

    public VersionInfo(List<String> versionInfoList) {
      String versionStr = versionInfoList.get(0).substring(1);
      if (!versionInfoList.get(0).startsWith("v")) {
        throw new RuntimeException();
      }
      parseVersionInfo(versionStr);
      if (versionInfoList.size() > 1) {
        List<String> versionInfos = versionInfoList.subList(1, versionInfoList.size());
        StringBuilder versionInfoBuilder = new StringBuilder();
        for (String versionInfo : versionInfos) {
          versionInfoBuilder.append("  ").append(versionInfo).append("\n");
        }
        updateInfo = versionInfoBuilder.toString();
      }
    }

    private void parseVersionInfo(String versionStr) {
      String[] versionInfoArr = versionStr.split("#");
      String[] versionInfo = versionInfoArr[0].split("\\.");
      primaryVersion = Integer.parseInt(versionInfo[0]);
      functionVersion = Integer.parseInt(versionInfo[1]);
      bugFixVersion = Integer.parseInt(versionInfo[2]);
      fullVersion = versionInfoArr[0];
      if (versionInfoArr.length >= 2) {
        needForceUpdate = Boolean.parseBoolean(versionInfoArr[1]);
      }
    }
  }
}
