package ${packageName}

import (
	"${containerImportPath}"
)

// ${dataManagerClassName} 配置数据管理器
//
// @author CCL
// @date ${date}
type ${dataManagerClassName} struct {
<#list beanAndContainerMap as beanName, containerName>
	${containerName} *container.${containerName}
</#list>
}

// New${dataManagerClassName} 创建配置数据管理器
func New${dataManagerClassName}() *${dataManagerClassName} {
	manager := &${dataManagerClassName}{}
<#list beanAndContainerMap as beanName, containerName>
	manager.${containerName} = container.New${containerName}()
</#list>
	return manager
}

// ${loadMethodName?cap_first} 加载全部配置表
func (m *${dataManagerClassName}) ${loadMethodName?cap_first}(rootPath string) error {
<#list beanAndContainerMap as beanName, containerName>
	if err := m.${containerName}.LoadData(rootPath); err != nil {
		return err
	}
</#list>
	return nil
}