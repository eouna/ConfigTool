-- 配置表枚举(自动生成)
-- @date ${date}
local enums = {}
<#list enumDefs as enumDef>

enums.${enumDef.name} = {
<#list enumDef.values as value>
    "${value}",
</#list>
}
</#list>

return enums