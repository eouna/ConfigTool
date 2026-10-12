/**
 * 配置表枚举定义(数据源: json)
 *
 * @generator com.eouna.configtool.generator.template.js.JsTemplateGenerator
 * @author CCL
 * @date ${date}
 */

export const Enums = Object.freeze({
<#list enumDefs as enumDef>
  /** ${enumDef.name} */
  ${enumDef.name}: Object.freeze([<#list enumDef.values as value>"${value}"<#if value_has_next>, </#if></#list>]),
</#list>
});

export default Enums;