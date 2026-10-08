//! 临时调试入口(自动生成, 不需要时可直接删除)
//!
//! 运行: cargo run -- ../../example

use ${crateName}::${dataManagerClassName};

fn main() {
    let root = std::env::args()
        .nth(1)
        .unwrap_or_else(|| "${excelLoadDir}".to_string());
    let mut manager = ${dataManagerClassName}::new();
    if let Err(error) = manager.${loadMethodName}(&root) {
        eprintln!("加载配置表失败: {}", error);
        std::process::exit(1);
    }
    println!("配置表加载完成, 资源目录: {}", root);
<#list containerDefs as def>
    println!("${def.className}: {} 条", manager.${def.fieldName}.cfg_map.len());
    for (id, cfg) in manager.${def.fieldName}.cfg_map.iter() {
        println!("    id={} => {:#?}", id, cfg);
    }
</#list>
}