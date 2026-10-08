-- 配置数据管理器(自动生成)
-- @date ${date}
local ${dataManagerClassName} = {}

${dataManagerClassName}.containers = {}
<#list containerDefs as def>
${dataManagerClassName}.${def.fieldName} = require("${def.moduleName}")
${dataManagerClassName}.containers["${def.className}"] = ${dataManagerClassName}.${def.fieldName}
</#list>

--- 创建管理器
function ${dataManagerClassName}.new()
    return ${dataManagerClassName}
end

--- 加载全部配置表
function ${dataManagerClassName}.${loadMethodName}(rootPath)
<#list containerDefs as def>
    ${dataManagerClassName}.${def.fieldName}.load(rootPath)
</#list>
    return true
end

return ${dataManagerClassName}