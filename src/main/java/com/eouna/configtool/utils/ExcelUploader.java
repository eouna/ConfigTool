package com.eouna.configtool.utils;

import com.eouna.configtool.configholder.ConfigDataBean;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.logger.TextAreaStepLogger;
import com.jcraft.jsch.Session;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;

/**
 * 服务器上传器
 *
 * @author CCL
 * @date 2023/4/4
 */
public class ExcelUploader {

  /** 临时压缩文件名 */
  private static final String TEMP_ZIP_FILE_NAME = "excel.zip";

  /** 本地服 */
  private static final String LOCAL_KEY = "Local";

  /**
   * 同步excel到服务器 1. 读取excel文件列表对应的需要更新的服务器模块信息 2. 按目录压缩 3. 上传解压按目录分发配置表
   *
   * @param excelFileList 待同步的服务器
   */
  public static synchronized void syncExcelToServer(
      List<File> excelFileList, List<String> selectedServer) {
    List<String> selectedServerCopy = new ArrayList<>(selectedServer);
    if (excelFileList.isEmpty()) {
      return;
    }
    TextAreaStepLogger textAreaStepLogger = new TextAreaStepLogger();
    textAreaStepLogger.info("开始同步excel文件");
    // 构建带目录结构的excel文件夹
    String excelBasePath =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getExcelConfigLoadPath();
    // 服务器同步相关配置
    ConfigDataBean.SyncConfig syncConfig = SystemConfigHolder.getInstance().getSyncConfig();
    // 模块目录和文件列表的映射
    Map<String, List<File>> moduleDirOfFile = new HashMap<>(8);
    excelFileList.forEach(
        file -> {
          String filteredBasePath =
              file.getAbsolutePath().replace(excelBasePath + File.separator, "");
          String[] splitPath = filteredBasePath.replace(File.separator, "/").split("/");
          if (splitPath.length > 1) {
            String moduleDir = splitPath[0];
            moduleDirOfFile.computeIfAbsent(moduleDir, k -> new ArrayList<>()).add(file);
          } else {
            moduleDirOfFile.computeIfAbsent("common", k -> new ArrayList<>()).add(file);
          }
        });
    if (selectedServerCopy.stream().anyMatch(s -> s.contains(LOCAL_KEY))) {
      // 本地同步模式
      if (syncFileToLocalPath(syncConfig, moduleDirOfFile)) {
        textAreaStepLogger.success("配置表同步成功");
      }
      selectedServerCopy.removeIf(s -> s.contains(LOCAL_KEY));
      if (selectedServerCopy.isEmpty()) {
        return;
      }
    }
    textAreaStepLogger.info("开始压缩excel文件");
    // 压缩excel文件
    File zipFile = zipExcelFileList(moduleDirOfFile);
    if (zipFile == null) {
      LoggerUtils.getTextareaLogger().error("压缩文件失败");
      return;
    }
    textAreaStepLogger.info("压缩excel文件结束");
    // 获取服务器的Session
    Session session =
        JschUtils.getSession(SystemConfigHolder.getInstance().getSyncConfig().getTargetServer());
    try {
      textAreaStepLogger.info("开始上传excel压缩文件");
      // 上传压缩文件
      JschUtils.uploadFile(session, zipFile, syncConfig.getTargetServer().getUploadTempFilePath());
      textAreaStepLogger.info("上传excel压缩文件结束");
      // 检测excel文件所属的文件并分发到服务器各个模块下 解压文件分发配置表到各个服务器
      textAreaStepLogger.info("构建迁移命令");
      // 构建excel文件迁移命令
      String excelCommandStr = SystemConfigHolder.getInstance().getSyncConfig().getExecuteCommand();
      LoggerUtils.getTextareaLogger().info("执行命令: {}", excelCommandStr);
      textAreaStepLogger.info("开始执行远端解压缩和复制文件命令");
      // 执行远程命令
      JschUtils.executeCommand(session, excelCommandStr);
      textAreaStepLogger.success("执行命令结束, 配置表上传成功");
    } catch (Exception e) {
      LoggerUtils.showErrorDialog("同步配置异常,err: " + e.getMessage(), e);
    } finally {
      if (session != null) {
        session.disconnect();
      }
    }
  }

  /**
   * 将配置表同步到本地 主要是后端程序使用
   *
   * <p>获取当前文件归属于哪一个excel模块 <br>
   * 获取excel模块下需要同步的本地文件夹列表 <br>
   * 遍历进行迁移 <br>
   *
   * @param moduleDirOfFile 需要更新的模块
   * @param syncConfig 同步配置
   */
  private static boolean syncFileToLocalPath(
      ConfigDataBean.SyncConfig syncConfig, Map<String, List<File>> moduleDirOfFile) {
    // 先判断生成目录是否合理
    String templateFileGenTargetDir =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir();
    String packageName = SystemConfigHolder.getInstance().getJavaTemplateConf().getPackageName();
    if (templateFileGenTargetDir.isEmpty()
        || !templateFileGenTargetDir.replace(File.separator, ".").contains(packageName)) {
      LoggerUtils.showErrorDialog("同步失败", "同步目标目录不为游戏工程路径, 当前路径: " + templateFileGenTargetDir);
      return false;
    }
    String projectName = packageName.substring(packageName.lastIndexOf(".") + 1);
    if (!templateFileGenTargetDir.contains(projectName)) {
      return false;
    }
    // 工程基础路径
    String baseProjectDir =
        templateFileGenTargetDir.substring(0, templateFileGenTargetDir.indexOf(projectName));
    // 构建需要更新的模块
    Map<String, List<ConfigDataBean.ServerLoadExcelDirConfBean>> serverUpdateModules =
        buildServerNeedUpdateModules(moduleDirOfFile);
    try {
      for (Entry<String, List<File>> moduleDirEntry : moduleDirOfFile.entrySet()) {
        String excelModuleName = moduleDirEntry.getKey();
        // excel目录绑定的本地目录
        List<ConfigDataBean.ServerLoadExcelDirConfBean> bindLocalExcelDir =
            serverUpdateModules.get(excelModuleName);
        if (bindLocalExcelDir == null) {
          continue;
        }
        for (ConfigDataBean.ServerLoadExcelDirConfBean excelDirConfBean : bindLocalExcelDir) {
          // 目标路径
          String targetPath =
              baseProjectDir
                  + File.separator
                  + excelDirConfBean.getBindAppModuleDir()
                  + File.separator
                  + syncConfig.getResourcePath();
          // 如果只是绑定了变化的某几个文件
          if (!excelDirConfBean.getBindExcelFileList().isEmpty()) {
            List<String> excelNameList =
                moduleDirEntry.getValue().stream().map(File::getName).collect(Collectors.toList());
            excelNameList.retainAll(excelDirConfBean.getBindExcelFileList());
            if (!excelNameList.isEmpty()) {
              List<File> updateFileList =
                  moduleDirEntry.getValue().stream()
                      .filter(file -> excelNameList.contains(file.getName()))
                      .collect(Collectors.toList());
              // 移动列表下的所有文件
              FileUtils.copyToDirectory(updateFileList, new File(targetPath));
            }
            continue;
          }
          // 移动列表下的所有文件
          FileUtils.copyToDirectory(moduleDirEntry.getValue(), new File(targetPath));
        }
      }
      return true;
    } catch (IOException e) {
      LoggerUtils.getTextareaLogger().error("复制文件错误", e);
    }
    return false;
  }

  /**
   * 压缩excel文件
   *
   * @param excelFileList excel文件列表
   */
  private static File zipExcelFileList(Map<String, List<File>> excelFileList) {
    File genDir =
        new File(
            SystemConfigHolder.getInstance()
                .getExcelConf()
                .getPath()
                .getTemplateFileGenTargetDir());
    if (!genDir.exists() && !genDir.mkdirs()) {
      LoggerUtils.getTextareaLogger().error("创建文件夹" + genDir.getAbsolutePath() + "失败");
      throw new RuntimeException("创建文件夹" + genDir.getAbsolutePath() + "失败");
    }
    // 将临时文件文件输出到生成文件夹中
    String tempZipFileName =
        SystemConfigHolder.getInstance().getExcelConf().getPath().getTemplateFileGenTargetDir()
            + File.separator
            + TEMP_ZIP_FILE_NAME;
    return ZipUtils.zipFileByFileNameMap(excelFileList, tempZipFileName);
  }

  /**
   * 通过excel文件列表构建服务器需要更新的列表
   *
   * @param moduleDirOfFile excel对应的文件夹和excel文件列表
   * @return 文件map
   */
  private static Map<String, List<ConfigDataBean.ServerLoadExcelDirConfBean>>
      buildServerNeedUpdateModules(Map<String, List<File>> moduleDirOfFile) {
    Map<String, List<ConfigDataBean.ServerLoadExcelDirConfBean>> serverModules = new HashMap<>(8);
    for (Entry<String, List<File>> moduleDirEntry : moduleDirOfFile.entrySet()) {
      String excelDir = moduleDirEntry.getKey();
      List<File> excelList = moduleDirEntry.getValue();
      // 当前excel文件下的文件发生变动时关联的服务器模块
      List<ConfigDataBean.ServerLoadExcelDirConfBean> serverLoadExcelDirConfBeans =
          SystemConfigHolder.getInstance().getLocalModuleDirByExcelDir(excelDir);
      if (serverLoadExcelDirConfBeans == null) {
        continue;
      }
      for (ConfigDataBean.ServerLoadExcelDirConfBean serverLoadExcelDirConfBean :
          serverLoadExcelDirConfBeans) {
        List<String> listenedExcelFileList = serverLoadExcelDirConfBean.getBindExcelFileList();
        if (listenedExcelFileList.isEmpty()) {
          serverModules
              .computeIfAbsent(excelDir, k -> new ArrayList<>())
              .add(serverLoadExcelDirConfBean);
          continue;
        }
        List<String> excelNameList =
            excelList.stream().map(File::getName).collect(Collectors.toList());
        excelNameList.retainAll(listenedExcelFileList);
        // 如果发生变化的excel文件被某些需要监听的模块包含则放入更新列表中
        if (excelNameList.size() > 0) {
          serverModules
              .computeIfAbsent(excelDir, k -> new ArrayList<>())
              .add(serverLoadExcelDirConfBean);
        }
      }
    }
    return serverModules;
  }
}