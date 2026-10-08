using System;
using System.Linq;

namespace ConfigTool.Cfg
{
    /// <summary>临时调试入口(自动生成, 不需要时可直接删除)</summary>
    internal static class Program
    {
        private static void Main(string[] args)
        {
            string root = args.Length > 0 ? args[0] : @"${excelLoadDir}";
            var manager = new ${dataManagerClassName}();
            try
            {
                manager.${loadMethodName}(root);
            }
            catch (AggregateException aggregate)
            {
                // Parallel.ForEach 会把各线程的异常包成AggregateException, 展开后输出全部失败原因
                string message = string.Join("; ", aggregate.Flatten().InnerExceptions.Select(item => item.Message));
                Console.Error.WriteLine("加载配置表失败: " + message);
                Environment.Exit(1);
            }
            catch (Exception exception)
            {
                Console.Error.WriteLine("加载配置表失败: " + exception.Message);
                Environment.Exit(1);
            }
            Console.WriteLine("配置表加载完成, 资源目录: " + root);
<#list containerDefs as def>
            Console.WriteLine("${def.className}: " + manager.${def.fieldName}.CfgBeanMap.Count + " 条");
            foreach (var pair in manager.${def.fieldName}.CfgBeanMap)
            {
                Console.WriteLine("    id=" + pair.Key + " => " + pair.Value);
            }
</#list>
        }
    }
}