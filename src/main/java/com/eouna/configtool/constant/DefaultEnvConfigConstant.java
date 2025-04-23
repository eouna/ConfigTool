package com.eouna.configtool.constant;

/**
 * 配置常量
 *
 * @author CCL
 */
public interface DefaultEnvConfigConstant {
  // region============================== 软件信息 =============================
  /** 工具默认版本号 */
  String TOOL_DEFAULT_VERSION = "v1.0.3";
  /** 作者名 */
  String AUTHOR = "CCL";
  /** 公司名 */
  String COM_NAME = "eouna";
  // endregion============================== 软件信息 ==============================
  /** 系统配置名 */
  String SYSTEM_CONFIG_NAME = "ExcelConfig.yaml";
  /** 系统配置路径 */
  String SYSTEM_CONFIG_PATH = "./config/" + SYSTEM_CONFIG_NAME;
  /** excel路径绑定的本地模块配置 */
  String LOCAL_LOAD_EXCEL_DIR_CONFIG_PATH = "./config/ExcelDirBindServerModuleConfLocal.txt";
  /** excel垂直位置显示个数 */
  int EXCEL_NAME_VERTICAL_SHOW_SIZE = 17;
  /** 日志区域显示最大条数 当前 */
  int LOG_AREA_MAX_SHOW_NUM = 1000;
  /** java热加载依赖路径lib的路径 */
  String DIR_JAVA_LIB_HOT_RELOAD_DEPEND_ON = "templatelib";

  /** 文件父子结构分隔符 */
  String EXCEL_STRUCTURE_DELIMITER = "_";
  /** 文件父子结构最大支持深度 TODO 目前仅支持一层结构,多层会出现数据重复加载问题 */
  int EXCEL_STRUCTURE_MAX_DEPTH = 1;

  /** 生成excel时发生异常是否立即退出 */
  boolean IS_GEN_EXCEL_ERROR_EXIT_NOW = false;

  // region============================== JAVA模板生成相关类名 =============================
  /** 基础bean模板的类名 */
  String BASE_BEAN_TEMPLATE_CLASS_NAME = "BaseCfgBean";
  /** 基础bean模板的类名 */
  String BASE_CONTAINER_TEMPLATE_CLASS_NAME = "BaseCfgContainer";
  /** 配置bean的路径 */
  String CFG_BEAN_PATH = "bean";
  /** 配置bean容器的路径 */
  String CONTAINER_PATH = "container";
  /** 配置bean容器的路径 */
  String CONTAINER_GEN_SUFFIX = "Container";
  // endregion============================== 模板生成相关 ==============================

  // region============================== Java模板名 =============================
  /** 基础模板bean */
  String BASE_CFG_BEAN_TEMPLATE_NAME = BASE_BEAN_TEMPLATE_CLASS_NAME + ".ftl";
  /** 基础模板bean */
  String CFG_BEAN_TEMPLATE_NAME = "CfgBean.ftl";
  /** 基础模板bean */
  String BASE_CFG_BEAN_CONTAINER_TEMPLATE_NAME = BASE_CONTAINER_TEMPLATE_CLASS_NAME + ".ftl";
  /** 配置表容器模板名 */
  String CFG_CONTAINER_TEMPLATE_NAME = "CfgContainer.ftl";
  // endregion============================== 模板名 ==============================

  interface ColorDefine {
    /** 安全色 */
    String SAFE = "#00FF7F";
    /** 危险色 */
    String DANGER = "#F51717FF";
  }
}
