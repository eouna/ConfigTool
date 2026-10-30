import type { CfgBean, FieldMeta, JsonRecord, JsonValue } from "../Types.js";
import {
  asBoolean,
  asNumber,
  asString,
  toList,
  toMap,
  toSet,
} from "../TypeConverters.js";

/**
 * ${beanClassName} 配置bean
 *
 * @excelName ${excelName}
 * @sheetName ${sheetName}
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 */
export class ${beanClassName} implements CfgBean {
  /** 配置表名(工作薄名), 同时也是json文件名 */
  static readonly SHEET_NAME = "${sheetName}";

  /** 主键字段名 */
  static readonly ID_NAME = "${idName}";

  /** 字段元数据 */
  static readonly FIELDS: readonly FieldMeta[] = Object.freeze([
<#list fields as field>
    { name: "${field.name}", type: "${field.tsType}", rawType: "${field.rawType}", desc: "${field.desc}" },
</#list>
  ]);

<#list fields as field>
  readonly ${field.propertyName}: ${field.tsType};
</#list>

  constructor(raw: JsonRecord) {
<#list fields as field>
    this["${field.name}"] = ${field.initializer};
</#list>
  }

  /** 主键值 */
  getId(): JsonValue {
    const id = (this as unknown as Record<string, JsonValue>)[
      ${beanClassName}.ID_NAME
    ];
    return id === undefined ? null : id;
  }

  /** 按字段名取值 */
  get(fieldName: string): unknown {
    return (this as unknown as Record<string, unknown>)[fieldName];
  }
}
