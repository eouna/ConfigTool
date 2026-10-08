//! 配置数据管理器(自动生成)
#![allow(non_snake_case)]

<#list containerDefs as def>
use crate::container::${def.moduleName}::${def.className};
</#list>

/// @author auto_generator
/// @date ${date}
#[derive(Default)]
pub struct ${dataManagerClassName} {
<#list containerDefs as def>
    /// ${def.className}
    pub ${def.fieldName}: ${def.className},
</#list>
}

impl ${dataManagerClassName} {
    /// 创建管理器
    pub fn new() -> Self {
        Self::default()
    }

    /// 并行加载全部配置表(每个容器一个线程)
    pub fn ${loadMethodName}(&mut self, root: &str) -> Result<(), String> {
        let mut errors: Vec<String> = Vec::new();
        std::thread::scope(|scope| {
<#list containerDefs as def>
            let ${def.fieldName}_handle = scope.spawn(|| self.${def.fieldName}.load(root));
</#list>
<#list containerDefs as def>
            match ${def.fieldName}_handle.join() {
                Ok(Ok(())) => {}
                Ok(Err(error)) => errors.push(error),
                Err(_) => errors.push("加载线程panic: ${def.className}".to_string()),
            }
</#list>
        });
        if errors.is_empty() {
            Ok(())
        } else {
            Err(errors.join("; "))
        }
    }
}