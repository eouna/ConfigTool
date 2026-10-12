<#list containerDefs as def>
import { ${def.className} } from './container/${def.moduleName}.js';
</#list>

/**
 * 配置数据管理器(数据源: json)
 *
 * @generator com.eouna.configtool.generator.template.js.JsTemplateGenerator
 * @author CCL
 * @date ${date}
 */
export class ${dataManagerClassName} {
  constructor() {
    /** 配置容器: 容器类名 -> 容器实例 */
    this.containers = new Map();
<#list containerDefs as def>
    /** ${def.className} */
    this.${def.fieldName} = new ${def.className}();
    this.containers.set('${def.className}', this.${def.fieldName});
</#list>
  }

  /**
   * 加载全部配置表
   *
   * @param {object} loader 数据源(FileJsonLoader / FetchJsonLoader / MemoryJsonLoader)
   * @returns {Promise<this>}
   */
  async ${loadMethodName}(loader) {
<#list containerDefs as def>
    await this.${def.fieldName}.load(loader);
</#list>
    return this;
  }

  /** 按容器类名获取容器 */
  getContainer(containerName) {
    return this.containers.get(containerName);
  }

  /** 全部容器 */
  getAllContainers() {
    return Array.from(this.containers.values());
  }
}