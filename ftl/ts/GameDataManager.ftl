<#list containerDefs as def>
import { ${def.className} } from "./container/${def.moduleName}.js";
</#list>
import type { BaseCfgContainer } from "./container/BaseCfgContainer.js";
import type { CfgBean } from "./Types.js";
import type { JsonLoader } from "./JsonLoader.js";

/**
 * 配置数据管理器(数据源: json)
 *
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 */
export class ${dataManagerClassName} {
  private readonly containers = new Map<
    string,
    BaseCfgContainer<CfgBean>
  >();

<#list containerDefs as def>
  readonly ${def.fieldName}: ${def.className};
</#list>

  constructor() {
<#list containerDefs as def>
    this.${def.fieldName} = new ${def.className}();
    this.containers.set(
      ${def.className}.name,
      this.${def.fieldName} as unknown as BaseCfgContainer<CfgBean>,
    );
</#list>
  }

  /** 加载全部配置表 */
  async ${loadMethodName}(loader: JsonLoader): Promise<this> {
<#list containerDefs as def>
    await this.${def.fieldName}.load(loader);
</#list>
    return this;
  }

  /** 按容器类名获取容器 */
  getContainer(containerName: string): BaseCfgContainer<CfgBean> | undefined {
    return this.containers.get(containerName);
  }

  /** 全部容器 */
  getAllContainers(): readonly BaseCfgContainer<CfgBean>[] {
    return Array.from(this.containers.values());
  }
}
