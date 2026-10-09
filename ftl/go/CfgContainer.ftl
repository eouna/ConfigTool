package ${containerPackageName}

import "${beanImportPath}"

// ${containerClassName} ${excelName}配置管理容器
//
// @excelName ${excelName}
// @sheetName ${sheetBean.sheetName}
// @author CCL
// @date ${date}
type ${containerClassName} struct {
	BaseCfgContainer[*${beanPackageName}.${beanClassName}]
<#list constantFields as constField>

	// ${constField.fieldDesc.fieldData}
	${constField.fieldName.fieldData?cap_first} ${constField.fieldType.fieldData}
</#list>
}

// New${containerClassName} 创建${excelName}配置管理容器
func New${containerClassName}() *${containerClassName} {
	c := &${containerClassName}{}
	c.BaseCfgContainer = newBaseCfgContainer[*${beanPackageName}.${beanClassName}]()
	c.Init(
		func() *${beanPackageName}.${beanClassName} {
			return &${beanPackageName}.${beanClassName}{}
		},
		[]string{
<#list bindExcelList as excelName>
			"${excelName}",
</#list>
		},
	)
	c.HasRelatedTable = ${hasRelatedTable?c}
	c.IsParentConfigNode = ${isParentNode?c}
<#if (constantFields?size > 0)>
	c.SetConstantTargets(map[string]any{
<#list constantFields as constField>
		"${constField.fieldName.fieldData}": &c.${constField.fieldName.fieldData?cap_first},
</#list>
	})
</#if>
	return c
}