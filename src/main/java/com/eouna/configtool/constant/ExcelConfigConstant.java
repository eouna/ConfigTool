package com.eouna.configtool.constant;

/**
 * 配置key常量
 *
 * @author CCL
 * @date 2023/3/3
 */
public interface ExcelConfigConstant {

  // region============= excel字段配置常量 前缀 EXCEL_FIELD 开头请勿修改 =============
  /** excel常量字段的前缀 */
  String PREFIX_EXCEL_FIELD = "EXCEL_FIELD";
  /** 字段描述列配置字符串 */
  String EXCEL_FIELD_DESC_ROW = "fieldDescRow";
  /** 字段描述列配置字符串 */
  String EXCEL_FIELD_TYPE_ROW = "fieldTypeRow";
  /** 字段描述列配置字符串 */
  String EXCEL_FIELD_NAME_ROW = "fieldNameRow";
  /** 字段描述列配置字符串 */
  String EXCEL_FIELD_DATA_RANGE_ROW = "fieldDataRangeRow";

  // endregion============= excel字段配置常量 =============

  // region============================== 配置路径 =============================
  /** excel配置加载路径 */
  String EXCEL_CONFIG_LOAD_PATH = "excelConfigLoadPath";
  /** 模板文件生成后保存的路径 */
  String TEMPLATE_GEN_FILE_DIR_PATH = "templateFileGenTargetDir";
  /** 模板路径 */
  String TEMPLATE_FILE_PATH = "templatePath";
  // endregion============================== 配置路径 ==============================

  /** 跳过单元格字符串 */
  String SKIPPED_CELL_STR = "client";
}
