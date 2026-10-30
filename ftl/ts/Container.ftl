import { BaseCfgContainer } from "./BaseCfgContainer.js";
import { ${beanClassName} } from "../bean/${beanModule}.js";
<#if hasConstants>
import { ${constantsClassName} } from "../constants_${beanModule}.js";
</#if>

/**
 * ${containerClassName} 配置管理容器(数据源: json)
 *
 * @excelName ${excelName}
 * @sheetName ${sheetName}
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 */
export class ${containerClassName} extends BaseCfgContainer<${beanClassName}> {
  /** 绑定的excel文件 */
  static readonly EXCEL_NAMES = [<#list bindExcelList as name>"${name}"<#if name_has_next>, </#if></#list>] as const;

  /** 是否存在分表关联 */
  static readonly HAS_RELATED_TABLE = ${hasRelatedTable?c};

  /** 是否为分表父节点 */
  static readonly IS_PARENT_NODE = ${isParentNode?c};

<#if hasConstants>
  /** 常量配置 */
  readonly constants = ${constantsClassName};

</#if>
  constructor() {
    super(${beanClassName});
  }
}
