-- ${containerClassName} 配置容器(自动生成)
-- @sheetName ${sheetName}
-- @date ${date}
local beanDef = require("${beanModule}")
local data = require("${dataModule}")
<#if hasConstants>
local constants = require("${constantsModule}")
</#if>

local ${containerClassName} = {}

${containerClassName}.className = "${containerClassName}"
${containerClassName}.beanDef = beanDef
${containerClassName}.cfgMap = data
${containerClassName}.constants = <#if hasConstants>constants<#else>{}</#if>

--- 根据ID获取配置
function ${containerClassName}.get(id)
    return ${containerClassName}.cfgMap[id]
end

--- 获取全部配置
function ${containerClassName}.getAll()
    return ${containerClassName}.cfgMap
end

--- 加载配置(数据已在生成期写入, 这里仅保持统一调用入口)
function ${containerClassName}.load(rootPath)
    return true
end

return ${containerClassName}