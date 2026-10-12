import { BaseCfgContainer } from './BaseCfgContainer.js';
import { ${beanClassName} } from '../bean/${beanModule}.js';
<#if hasConstants>
import { ${constantsClassName} } from '../constants_${beanModule}.js';
</#if>

/**
 * ${containerClassName} 配置管理容器(数据源: json)
 *
 * @excelName ${excelName}
 * @sheetName ${sheetName}
 * @generator com.eouna.configtool.generator.template.js.JsTemplateGenerator
 * @author CCL
 * @date ${date}
 */
export class ${containerClassName} extends BaseCfgContainer {
  /** 绑定的excel文件 */
  static EXCEL_NAMES = [<#list bindExcelList as name>"${name}"<#if name_has_next>, </#if></#list>];

  /** 是否存在分表关联 */
  static HAS_RELATED_TABLE = ${hasRelatedTable?c};

  /** 是否为分表父节点 */
  static IS_PARENT_NODE = ${isParentNode?c};

  constructor() {
    super(${beanClassName});
<#if hasConstants>
    /** 常量配置 */
    this.constants = ${constantsClassName};
</#if>
  }
}