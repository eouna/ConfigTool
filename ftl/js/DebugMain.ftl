/**
 * 临时调试入口(由配置表工具生成, 不需要时可直接删除)
 *
 * 运行(node >= 18, 可在任意目录执行):
 *   node debug/main.js
 * 也可以显式传入json目录:
 *   node debug/main.js ../../out/json
 */
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { ${dataManagerClassName} } from '../GameDataManager.js';
import { FileJsonLoader } from '../JsonLoader.js';

/** 默认json目录: <输出目录>/json (相对本文件定位, 与执行目录无关) */
const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const defaultJsonDir = path.resolve(scriptDir, '..', '..', 'json');
const jsonDir = process.argv[2] || defaultJsonDir;

const manager = new ${dataManagerClassName}();

await manager.${loadMethodName}(new FileJsonLoader(jsonDir));
console.log('配置表加载完成, json目录: ' + jsonDir);
<#list containerDefs as def>
{
  const container = manager.${def.fieldName};
  console.log('${def.className}: ' + container.size + ' 条');
<#if printLoadData>
  for (const bean of container.getAll()) {
    const parts = [];
    for (const field of container.beanClass.FIELDS) {
      parts.push(field.name + '=' + JSON.stringify(bean[field.name]));
    }
    console.log('    ' + parts.join(', '));
  }
</#if>
}
</#list>
