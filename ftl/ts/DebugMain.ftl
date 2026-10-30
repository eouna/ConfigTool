/**
 * 临时调试入口(由配置表工具生成, 不需要时可直接删除)
 *
 * 运行:
 *   npm install
 *   npm run build
 *   node dist/debug/main.js [json目录]
 *
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 */
import * as path from "node:path";
import { fileURLToPath } from "node:url";
import { ${dataManagerClassName} } from "../GameDataManager.js";
import { FileJsonLoader } from "../JsonLoader.js";

/** 默认json目录: <#if isSplitMultiJson><输出目录>/json<#else><输出目录>(读取 out.json)</#if> */
const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const defaultJsonDir = path.resolve(
  scriptDir,
  "..",
  "..",
  ".."<#if isSplitMultiJson>,
  "json"</#if>,
);
const jsonDir = process.argv[2] ?? defaultJsonDir;

const manager = new ${dataManagerClassName}();

await manager.${loadMethodName}(new FileJsonLoader(jsonDir));
console.log("配置表加载完成, json目录: " + jsonDir);
<#list containerDefs as def>
{
  const container = manager.${def.fieldName};
  console.log("${def.className}: " + container.size + " 条");
<#if printLoadData>
  for (const bean of container.getAll()) {
    const parts: string[] = [];
    for (const field of container.beanClass.FIELDS) {
      parts.push(field.name + "=" + JSON.stringify(bean.get(field.name)));
    }
    console.log("    " + parts.join(", "));
  }
</#if>
}
</#list>
