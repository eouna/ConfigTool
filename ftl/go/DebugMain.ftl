package main

import (
	"fmt"
	"os"
	"sort"

	genpackage "${moduleName}"
)

// 该文件由配置表工具临时生成, 仅用于本地调试读取配置表, 不需要时可直接删除
//
// 运行示例(在生成目录下): go run ./debug ../../example
//
// @author CCL
// @date ${date}
func main() {
	rootPath := "${excelLoadDir}"
	if len(os.Args) > 1 {
		rootPath = os.Args[1]
	}
	manager := genpackage.New${dataManagerClassName}()
	if err := manager.${loadMethodName?cap_first}(rootPath); err != nil {
		fmt.Println("加载配置表失败:", err)
		os.Exit(1)
	}
	fmt.Printf("配置表加载完成, 资源目录: %s\n", rootPath)
<#list beanAndContainerMap as beanName, containerName>
	{
		cfgMap := manager.${containerName}.CfgBeanMap
		ids := make([]int, 0, len(cfgMap))
		for id := range cfgMap {
			ids = append(ids, int(id))
		}
		sort.Ints(ids)
		fmt.Printf("${containerName}: %d 条\n", len(ids))
		for _, id := range ids {
			fmt.Printf("    id=%d => %+v\n", id, cfgMap[int32(id)])
		}
	}
</#list>
}