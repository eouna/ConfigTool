package ${packageName}

<#assign hasTime = false>
<#list dataStruct.excelFieldInfoList as excelFieldInfo>
<#if excelFieldInfo.fieldType.fieldData?contains("time.Time")><#assign hasTime = true></#if>
</#list>
<#if hasTime>
import "time"

</#if>
// ${beanClassName} 配置bean
//
// @excelName ${dataStruct.fileName}
// @sheetName ${dataStruct.sheetName}
// @author Auto.Generator
// @date ${date}
// 枚举类型统一声明在 Enums.go 中
type ${beanClassName} struct {
	${parentClass}
<#list dataStruct.excelFieldInfoList as excelFieldInfo>
	// ${excelFieldInfo.fieldDesc.fieldData}
	${excelFieldInfo.fieldName.fieldData?cap_first} ${excelFieldInfo.fieldType.fieldData} `cfg:"${excelFieldInfo.fieldName.fieldData}"`
</#list>
}

// ${beanClassName}ExcelName 配置表名
const ${beanClassName}ExcelName = "${dataStruct.fileName}"

// ${beanClassName}SheetName 配置表工作薄名
const ${beanClassName}SheetName = "${dataStruct.sheetName}"