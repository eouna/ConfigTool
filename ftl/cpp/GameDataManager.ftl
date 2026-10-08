#pragma once

#include <future>
#include <stdexcept>
#include <string>
#include <vector>

#include "CfgDefs.h"
<#list containerDefs as def>
#include "container/${def.className}.h"
</#list>

/// 配置数据管理器(自动生成)
/// @date ${date}
class ${dataManagerClassName} {
 public:
<#list containerDefs as def>
    /// ${def.className}
    ${def.className}& ${def.accessorName}() { return ${def.memberName}; }
    const ${def.className}& ${def.accessorName}() const { return ${def.memberName}; }

</#list>
    /// 并行加载全部配置表(每个容器一个线程, 全部join后才返回)
    void ${loadMethodName}(const std::string& resourceRootPath) {
        std::vector<std::future<void>> futures;
        futures.reserve(${containerDefs?size});
<#list containerDefs as def>
        futures.emplace_back(std::async(std::launch::async,
                                        [this, &resourceRootPath] { ${def.memberName}.Load(resourceRootPath); }));
</#list>
        std::vector<std::string> errors;
        for (auto& future : futures) {
            try {
                future.get();
            } catch (const std::exception& exception) {
                errors.emplace_back(exception.what());
            }
        }
        if (!errors.empty()) {
            throw std::runtime_error(cfg::Join(errors, "; "));
        }
    }

 private:
<#list containerDefs as def>
    ${def.className} ${def.memberName};
</#list>
};