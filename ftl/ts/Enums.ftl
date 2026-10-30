/**
 * 配置表枚举定义(数据源: json)
 *
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 */

export const Enums = Object.freeze({
<#list enumDefs as enumDef>
  "${enumDef.name?js_string}": Object.freeze([<#list enumDef.values as value>"${value?js_string}"<#if value_has_next>, </#if></#list>] as const),
</#list>
});

export default Enums;
