#include <exception>
#include <iostream>
#include <string>

#include "GameDataManager.h"

/// 临时调试入口(自动生成, 不需要时可直接删除)
/// 运行: ./config_tool_cpp <excel根目录>
int main(int argc, char** argv) {
    const std::string rootPath = argc > 1 ? argv[1] : "${excelLoadDir}";
    ${dataManagerClassName} manager;
    try {
        manager.${loadMethodName}(rootPath);
    } catch (const std::exception& exception) {
        std::cerr << "加载配置表失败: " << exception.what() << std::endl;
        return 1;
    }
    std::cout << "配置表加载完成, 资源目录: " << rootPath << std::endl;
<#list containerDefs as def>
    std::cout << "${def.className}: " << manager.${def.accessorName}().Size() << " 条" << std::endl;
    for (const auto& pair : manager.${def.accessorName}().GetAll()) {
        std::cout << "    id=" << pair.first << " => " << pair.second.ToString() << std::endl;
    }
</#list>
    return 0;
}