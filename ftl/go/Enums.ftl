package ${packageName}

// 该文件由配置表工具自动生成, 集中声明所有配置表中出现的枚举类型
//
// Go 的枚举是包级类型, 而分表时子表与父表处于同一个包, 因此统一在此声明, 避免重复声明或找不到类型
//
// @author CCL
// @date ${date}
<#list enumMap as enumClassName, enumValues>

// ${enumClassName} 枚举
type ${enumClassName} string

const (
<#list enumValues as enumName>
	${enumClassName}${enumName?cap_first} ${enumClassName} = "${enumName}"
</#list>
)

// Get${enumClassName}ByStr 根据字符串获取枚举
func Get${enumClassName}ByStr(str string) (${enumClassName}, bool) {
	switch ${enumClassName}(str) {
<#list enumValues as enumName>
	case ${enumClassName}${enumName?cap_first}:
		return ${enumClassName}${enumName?cap_first}, true
</#list>
	}
	return "", false
}
</#list>