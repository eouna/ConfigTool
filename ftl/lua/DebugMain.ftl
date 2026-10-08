-- 临时调试入口(自动生成, 不需要时可直接删除)
-- 运行: lua main.lua
local ${dataManagerClassName} = require("${dataManagerModule}")
local enums = require("enums")

--- 递归格式化table, 便于查看解析结果
local function formatValue(value)
    if type(value) ~= "table" then
        return tostring(value)
    end
    local parts = {}
    for key, item in pairs(value) do
        if type(key) == "number" then
            parts[#parts + 1] = "[" .. tostring(key) .. "]=" .. formatValue(item)
        else
            parts[#parts + 1] = tostring(key) .. "=" .. formatValue(item)
        end
    end
    return "{" .. table.concat(parts, ", ") .. "}"
end

${dataManagerClassName}.${loadMethodName}("${excelLoadDir}")
print("配置表加载完成")
<#list containerDefs as def>
do
    local container = ${dataManagerClassName}.${def.fieldName}
    local count = 0
    for _ in pairs(container.cfgMap) do
        count = count + 1
    end
    print("${def.className}: " .. count .. " 条")
    for id, cfg in pairs(container.cfgMap) do
        local parts = {}
        for _, field in ipairs(container.beanDef.fields) do
            parts[#parts + 1] = field.name .. "=" .. formatValue(cfg[field.name])
        end
        print("    " .. table.concat(parts, ", "))
    end
end
</#list>