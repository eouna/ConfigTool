/**
 * ${beanClassName} 配置bean
 *
 * @excelName ${excelName}
 * @sheetName ${sheetName}
 * @generator com.eouna.configtool.generator.template.js.JsTemplateGenerator
 * @author CCL
 * @date ${date}
 */

export class ${beanClassName} {
  /** 配置表名(工作薄名), 同时也是json文件名 */
  static SHEET_NAME = "${sheetName}";

  /** 主键字段名 */
  static ID_NAME = "${idName}";

  /**
   * 字段元数据
   * name: 字段名, type: 目标类型, rawType: 配置表中的类型写法, desc: 字段描述
   */
  static FIELDS = [
<#list fields as field>
    { name: "${field.name}", type: "${field.type}", rawType: "${field.rawType}", desc: "${field.desc}" },
</#list>
  ];

  /**
   * @param {object} raw 单条配置的json数据
   */
  constructor(raw) {
    Object.assign(this, raw || {});
  }

  /** 主键值 */
  getId() {
    return this[${beanClassName}.ID_NAME];
  }

  /** 按字段名取值 */
  get(fieldName) {
    return this[fieldName];
  }
}