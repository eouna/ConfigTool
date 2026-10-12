package ${packageName};

import java.util.Map;

import javax.annotation.processing.Generated;

/**
 * ${dataManagerClassName} 主入口(自动生成, 仅用于本地调试读取配置表, 不需要时可直接删除)
 *
 * <p>运行示例:
 *
 * <pre>
 * java ${packageName}.${mainClassName} &lt;配置表根目录&gt;
 * </pre>
 *
 * @author CCL
 * @date ${date}
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ${mainClassName} {

  public static void main(String[] args) throws Exception {
    String rootPath = args != null && args.length > 0 ? args[0] : "${excelLoadDir}";
    ${dataManagerClassName}.loadAllData(rootPath);
    System.out.println("配置表加载完成, 资源目录: " + rootPath);
<#list beanAndContainerMap as beanName, containerName>
    System.out.println("${beanName}: " + ${dataManagerClassName}.get${beanName}Map().size() + " 条");
<#if printLoadData>
    for (Map.Entry<Integer, ${beanName}> entry : ${dataManagerClassName}.get${beanName}Map().entrySet()) {
      System.out.println("    id=" + entry.getKey() + " => " + entry.getValue());
    }
</#if>
</#list>
  }
}
