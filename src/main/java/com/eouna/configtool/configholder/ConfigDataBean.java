package com.eouna.configtool.configholder;

import java.util.ArrayList;
import java.util.List;

import com.eouna.configtool.utils.FileUtils;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

/**
 * 配置相关的bean
 *
 * @author CCL
 */
public class ConfigDataBean {
  @CfgDataBean
  public static class ExcelConf {
    /** excel相关行 @example alias name demo */
    @ConfigAutowired(aliasName = "fieldRow")
    private ExcelFieldConf fieldRows;

    /** excel生成路径配置 */
    @ConfigSettingControl(desc = "excel生成路径")
    private ExcelGenPathConf path;

    public ExcelFieldConf getFieldRows() {
      return fieldRows;
    }

    public void setFieldRows(ExcelFieldConf fieldRows) {
      this.fieldRows = fieldRows;
    }

    public ExcelGenPathConf getPath() {
      return path;
    }

    public void setPath(ExcelGenPathConf path) {
      this.path = path;
    }
  }

  @CfgDataBean
  public static class ExcelFieldConf {
    /** 字段描述列 */
    private int fieldDescRow;
    /** 字段类型列 */
    private int fieldTypeRow;
    /** 字段名列 */
    private int fieldNameRow;
    /** 字段数值范围列 */
    private int fieldDataRangeRow;

    /** 配置字段是否将下划线后的字符转为大写 */
    private boolean isUnderLineTransUpper;

    public boolean getIsUnderLineTransUpper() {
      return isUnderLineTransUpper;
    }

    public void setIsUnderLineTransUpper(boolean isUnderLineTransUpper) {
      this.isUnderLineTransUpper = isUnderLineTransUpper;
    }

    public int getFieldTypeRow() {
      return fieldTypeRow;
    }

    public void setFieldTypeRow(int fieldTypeRow) {
      this.fieldTypeRow = fieldTypeRow;
    }

    public int getFieldDescRow() {
      return fieldDescRow;
    }

    public void setFieldDescRow(int fieldDescRow) {
      this.fieldDescRow = fieldDescRow;
    }

    public int getFieldNameRow() {
      return fieldNameRow;
    }

    public void setFieldNameRow(int fieldNameRow) {
      this.fieldNameRow = fieldNameRow;
    }

    public Integer getFieldDataRangeRow() {
      return fieldDataRangeRow;
    }

    public void setFieldDataRangeRow(Integer fieldDataRangeRow) {
      this.fieldDataRangeRow = fieldDataRangeRow;
    }
  }

  @CfgDataBean
  public static class ExcelGenPathConf {

    /** excel 配置加载路径 */
    private String excelConfigLoadPath;
    /** 模板文件生成后的文件保存路径 */
    private String templateFileGenTargetDir;
    /** 模板文件路径 */
    @ConfigSettingControl(desc = "模板文件路径", bindTextFieldComponent = TextField.class)
    private String templatePath;

    public String getExcelConfigLoadPath() {
      return FileUtils.getAbsolutePath(excelConfigLoadPath);
    }

    public void setExcelConfigLoadPath(String excelConfigLoadPath) {
      this.excelConfigLoadPath = excelConfigLoadPath;
    }

    public String getTemplatePath() {
      return FileUtils.getAbsolutePath(templatePath);
    }

    public void setTemplatePath(String templatePath) {
      this.templatePath = templatePath;
    }

    public String getTemplateFileGenTargetDir() {
      return FileUtils.getAbsolutePath(templateFileGenTargetDir);
    }

    public void setTemplateFileGenTargetDir(String templateFileGenTargetDir) {
      this.templateFileGenTargetDir = templateFileGenTargetDir;
    }
  }

  /** java模板配置 */
  @CfgDataBean
  public static class JavaTemplateConf {
    /** java程序中生成后的基础包名 */
    @ConfigSettingControl(desc = "包名", bindTextFieldComponent = TextField.class)
    private String packageName;
    /** excel中用于标识服务端数据范围跳过字段 可用于跳过列,忽略数据列 */
    @ConfigSettingControl(desc = "数据范围跳过字段", bindTextFieldComponent = TextField.class)
    private String dataRangeServerSkipStr;
    /** excel中用于标识客户端数据范围跳过字段 可用于跳过列,忽略数据列 */
    @ConfigSettingControl(desc = "数据范围跳过字段", bindTextFieldComponent = TextField.class)
    private String dataRangeClientSkipStr;
    /** 生成模板的数据管理类名 */
    @ConfigSettingControl(desc = "数据管理类名", bindTextFieldComponent = TextField.class)
    private String dataManagerClassName;
    /** 生成模板的数据管理运行方法名 */
    @ConfigSettingControl(desc = "数据管理运行方法名", bindTextFieldComponent = TextField.class)
    private String dataManagerLoadDataCaller;
    /** 基础配置bean的ID名 */
    @ConfigSettingControl(desc = "基础配置bean的ID名", bindTextFieldComponent = TextField.class)
    private String baseBeanIdName;
    /** 是否保持配置表加载器的相对路径,保存到配置文件时,是否是全路径保存,如果否则保存以程序运行为根路径的的相对路径 */
    @ConfigSettingControl(desc = "是否保持配置表加载器的相对路径", bindTextFieldComponent = TextField.class)
    private boolean keepBindExcelRelativePath;

    public String getPackageName() {
      return packageName;
    }

    public void setPackageName(String packageName) {
      this.packageName = packageName;
    }

    public String getDataRangeServerSkipStr() {
      return dataRangeServerSkipStr;
    }

    public void setDataRangeServerSkipStr(String dataRangeServerSkipStr) {
      this.dataRangeServerSkipStr = dataRangeServerSkipStr;
    }

    public String getDataRangeClientSkipStr() {
      return dataRangeClientSkipStr;
    }

    public void setDataRangeClientSkipStr(String dataRangeClientSkipStr) {
      this.dataRangeClientSkipStr = dataRangeClientSkipStr;
    }

    public String getDataManagerClassName() {
      return dataManagerClassName;
    }

    public void setDataManagerClassName(String dataManagerClassName) {
      this.dataManagerClassName = dataManagerClassName;
    }

    public String getDataManagerLoadDataCaller() {
      return dataManagerLoadDataCaller;
    }

    public void setDataManagerLoadDataCaller(String dataManagerLoadDataCaller) {
      this.dataManagerLoadDataCaller = dataManagerLoadDataCaller;
    }

    public void setBaseBeanIdName(String baseBeanIdName) {
      this.baseBeanIdName = baseBeanIdName;
    }

    public String getBaseBeanIdName() {
      return baseBeanIdName;
    }

    public boolean isKeepBindExcelRelativePath() {
      return keepBindExcelRelativePath;
    }

    public void setKeepBindExcelRelativePath(boolean keepBindExcelRelativePath) {
      this.keepBindExcelRelativePath = keepBindExcelRelativePath;
    }
  }

  @CfgDataBean
  public static class JsonTemplateConf {

    /** 生成时是否分为多个json文件 */
    @ConfigSettingControl(desc = "生成时是否分为多个json文件", bindTextFieldComponent = TextField.class)
    private boolean isSplitMultiJson;

    public boolean isSplitMultiJson() {
      return isSplitMultiJson;
    }

    public void setSplitMultiJson(boolean splitMultiJson) {
      isSplitMultiJson = splitMultiJson;
    }
  }

  @CfgDataBean
  public static class SyncConfig {
    /** 服务器列表 */
    private String serverList;
    /** excel资源放置路径 */
    @ConfigSettingControl(desc = "excel资源放置路径")
    private String resourcePath;
    /** 服务器连接信息 */
    @ConfigSettingControl(desc = "服务器连接信息")
    private ServerConnectInfo targetServer;
    /** 脚本执行命令行 */
    @ConfigSettingControl(desc = "远程命令行", bindTextFieldComponent = TextArea.class)
    private String executeCommand;

    public String getServerList() {
      return serverList;
    }

    public void setServerList(String serverList) {
      this.serverList = serverList;
    }

    public ServerConnectInfo getTargetServer() {
      return targetServer;
    }

    public void setTargetServer(ServerConnectInfo targetServer) {
      this.targetServer = targetServer;
    }

    public String getResourcePath() {
      return resourcePath;
    }

    public void setResourcePath(String resourcePath) {
      this.resourcePath = resourcePath;
    }

    public String getExecuteCommand() {
      return executeCommand;
    }

    public void setExecuteCommand(String executeCommand) {
      this.executeCommand = executeCommand;
    }
  }

  @CfgDataBean
  public static class ServerConnectInfo {

    /** 需要同步的服务器IP */
    @ConfigSettingControl(desc = "ip", bindTextFieldComponent = TextField.class)
    private String serverIp;
    /** 服务器用户名 */
    @ConfigSettingControl(desc = "用户名", bindTextFieldComponent = TextField.class)
    private String username;
    /** 服务器用户密码 */
    @ConfigSettingControl(desc = "密码", bindTextFieldComponent = PasswordField.class)
    private String userPass;
    /** 连接端口 */
    @ConfigSettingControl(desc = "端口", bindTextFieldComponent = TextField.class)
    private int port;
    /** 服务器路径 */
    private String serverPath;
    /** 文件上传临时路径 */
    private String uploadTempFilePath;

    public String getServerIp() {
      return serverIp;
    }

    public void setServerIp(String serverIp) {
      this.serverIp = serverIp;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getUserPass() {
      return userPass;
    }

    public void setUserPass(String userPass) {
      this.userPass = userPass;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    public String getServerPath() {
      return serverPath;
    }

    public void setServerPath(String serverPath) {
      this.serverPath = serverPath;
    }

    public String getUploadTempFilePath() {
      return uploadTempFilePath;
    }

    public void setUploadTempFilePath(String uploadTempFilePath) {
      this.uploadTempFilePath = uploadTempFilePath;
    }
  }

  /** 服务器excel加载路径配置bean */
  public static class ServerLoadExcelDirConfBean {
    /** 绑定的App模块路径 */
    private String bindAppModuleDir;
    /** 监听的excel文件列表 */
    private List<String> bindExcelFileList = new ArrayList<>();

    public String getBindAppModuleDir() {
      return bindAppModuleDir;
    }

    public void setBindAppModuleDir(String bindAppModuleDir) {
      this.bindAppModuleDir = bindAppModuleDir;
    }

    public List<String> getBindExcelFileList() {
      return bindExcelFileList;
    }

    public void setBindExcelFileList(List<String> bindExcelFileList) {
      this.bindExcelFileList = bindExcelFileList;
    }
  }

  @CfgDataBean
  public static class CacheConfig {
    /** sqlite配置 */
    private SqliteConfig sqliteConfig;
  }

  @CfgDataBean
  public static class SqliteConfig {
    /** 是否启用 sqlite 作为缓存 */
    private boolean enable;
    /** 用户名 */
    private String user;
    /** 密码 */
    private String pass;

    public boolean isEnable() {
      return enable;
    }

    public void setEnable(boolean enable) {
      this.enable = enable;
    }

    public String getUser() {
      return user;
    }

    public void setUser(String user) {
      this.user = user;
    }

    public String getPass() {
      return pass;
    }

    public void setPass(String pass) {
      this.pass = pass;
    }
  }
}
