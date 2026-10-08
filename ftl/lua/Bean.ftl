-- ${beanClassName} 配置bean定义(自动生成)
-- @excelName ${excelName}
-- @sheetName ${sheetName}
-- @date ${date}
local ${beanClassName} = {
    EXCEL_NAME = "${excelName}",
    SHEET_NAME = "${sheetName}",
    ID_NAME = "${idName}",
    fields = {
<#list fields as field>
        { name = "${field.name}", type = "${field.type}", desc = "${field.desc}" },
</#list>
    },
}

return ${beanClassName}