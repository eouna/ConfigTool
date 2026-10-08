using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace ConfigTool.Cfg
{
    /// <summary>
    /// 配置数据管理器(自动生成)
    /// @date ${date}
    /// </summary>
    public class ${dataManagerClassName}
    {
<#list containerDefs as def>
        /// <summary>${def.className}</summary>
        public ${def.className} ${def.fieldName} { get; } = new ${def.className}();
</#list>

        /// <summary>全部容器</summary>
        public IReadOnlyList<ICfgContainer> Containers { get; }

        public ${dataManagerClassName}()
        {
            Containers = new List<ICfgContainer>
            {
<#list containerDefs as def>
                ${def.fieldName},
</#list>
            };
        }

        /// <summary>并行加载全部配置表(默认并发度为CPU核心数)</summary>
        public void ${loadMethodName}(string rootPath)
        {
            ${loadMethodName}(rootPath, Environment.ProcessorCount);
        }

        /// <summary>指定并发度并行加载全部配置表</summary>
        public void ${loadMethodName}(string rootPath, int maxDegreeOfParallelism)
        {
            if (maxDegreeOfParallelism <= 0)
            {
                maxDegreeOfParallelism = 1;
            }
            var options = new ParallelOptions { MaxDegreeOfParallelism = maxDegreeOfParallelism };
            Parallel.ForEach(Containers, options, container => container.LoadData(rootPath));
        }
    }
}