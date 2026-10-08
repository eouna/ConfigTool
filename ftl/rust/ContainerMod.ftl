//! container模块(自动生成)
#![allow(non_snake_case)]

pub mod base_cfg_container;
<#list containerModules as moduleName>
pub mod ${moduleName};
</#list>

pub use base_cfg_container::*;