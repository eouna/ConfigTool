/**
 * TypeScript配置代码公共类型
 *
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 */

export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

export type JsonRecord = Record<string, unknown>;

export interface FieldMeta {
  readonly name: string;
  readonly type: string;
  readonly rawType: string;
  readonly desc: string;
}

export interface CfgBean {
  getId(): JsonValue;
  get(fieldName: string): unknown;
}

export interface CfgBeanConstructor<T extends CfgBean> {
  new (raw: JsonRecord): T;
  readonly SHEET_NAME: string;
  readonly ID_NAME: string;
  readonly FIELDS: readonly FieldMeta[];
}

export function isJsonRecord(value: unknown): value is JsonRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
