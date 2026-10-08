package ${packageName}

// BaseCfgBean 配置表基类
//
// @author CCL
type BaseCfgBean struct {
	// ${idName}
	${idName?cap_first} int32 `cfg:"${idName}"`
}

// Get${idName?cap_first} 返回${idName}
func (b *BaseCfgBean) Get${idName?cap_first}() int32 {
	return b.${idName?cap_first}
}