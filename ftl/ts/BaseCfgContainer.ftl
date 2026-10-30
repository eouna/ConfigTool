import type {
  CfgBean,
  CfgBeanConstructor,
  FieldMeta,
  JsonRecord,
} from "../Types.js";
import { isJsonRecord } from "../Types.js";
import { asJsonRecord } from "../TypeConverters.js";
import type { JsonLoader } from "../JsonLoader.js";

/**
 * 配置容器基类(数据源: json)
 *
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 */
export class BaseCfgContainer<T extends CfgBean> {
  readonly beanClass: CfgBeanConstructor<T>;

  private cfgBeanMap: Map<string, T> = new Map();
  private cfgBeans: T[] = [];
  private loaded = false;

  constructor(beanClass: CfgBeanConstructor<T>) {
    this.beanClass = beanClass;
  }

  /** 配置表名(工作薄名) */
  get sheetName(): string {
    return this.beanClass.SHEET_NAME;
  }

  /** 从数据源加载本表配置 */
  async load(loader: JsonLoader): Promise<this> {
    const raw = await loader.loadSheet(this.sheetName);
    return this.loadFromObject(raw);
  }

  /** 直接使用JSON数据填充容器, 便于测试或接入自定义数据源 */
  loadFromObject(raw: unknown): this {
    const rawRecord = asJsonRecord(raw);
    const map = new Map<string, T>();
    for (const [key, value] of Object.entries(rawRecord)) {
      // json中key为0的是枚举定义, 不是数据行
      if (key === "0") {
        continue;
      }
      if (!isJsonRecord(value)) {
        continue;
      }
      this.validateBeanFields(value);
      const bean = new this.beanClass(value);
      const id = bean.getId();
      map.set(id === null || id === undefined ? key : String(id), bean);
    }
    this.cfgBeanMap = map;
    this.cfgBeans = Array.from(map.values());
    this.loaded = true;
    return this;
  }

  /** 按字段 rawType 校验 List/Set/Map 的数量限制 */
  private validateBeanFields(raw: JsonRecord): void {
    for (const field of this.beanClass.FIELDS) {
      if (Object.prototype.hasOwnProperty.call(raw, field.name)) {
        this.validateFieldValue(
          field.rawType,
          raw[field.name],
          field.name,
        );
      }
    }
  }

  /** 递归校验集合类型, 数量必须严格等于类型中配置的限制值 */
  private validateFieldValue(
    rawType: string,
    value: unknown,
    fieldName: string,
  ): void {
    const raw = (rawType || "").trim();
    const sequenceMatch = raw.match(/^(list|set)<(.*)>\{(.)(\d*)\}$/i);
    if (sequenceMatch) {
      if (!Array.isArray(value)) {
        return;
      }
      this.checkSizeLimit(value.length, sequenceMatch[4], fieldName);
      for (const item of value) {
        this.validateFieldValue(sequenceMatch[2], item, fieldName);
      }
      return;
    }

    const mapMatch = raw.match(/^map<(.*?)\{(.)\}(.*)>\{(.)(\d*)\}$/i);
    if (mapMatch && isJsonRecord(value)) {
      const values = Object.values(value);
      this.checkSizeLimit(values.length, mapMatch[5], fieldName);
      for (const mapValue of values) {
        this.validateFieldValue(mapMatch[3], mapValue, fieldName);
      }
    }
  }

  private checkSizeLimit(
    actualSize: number,
    limitText: string,
    fieldName: string,
  ): void {
    const limit = Number(limitText || "0");
    if (limit > 0 && actualSize !== limit) {
      throw new Error(
        this.sheetName +
          " 字段 " +
          fieldName +
          " 对应的数据数量: " +
          actualSize +
          " 与限制值: " +
          limit +
          " 不一致",
      );
    }
  }

  /** 按主键获取配置(数字与字符串主键均可) */
  getById(id: unknown): T | undefined {
    return this.cfgBeanMap.get(String(id));
  }

  /** 按主键获取配置(同 getById) */
  get(id: unknown): T | undefined {
    return this.getById(id);
  }

  /** 是否包含指定主键 */
  has(id: unknown): boolean {
    return this.getById(id) !== undefined;
  }

  /** 获取全部配置 */
  getAll(): readonly T[] {
    return this.cfgBeans;
  }

  /** 配置条数 */
  get size(): number {
    return this.cfgBeanMap.size;
  }

  /** 遍历全部配置 */
  forEach(callback: (bean: T) => void): void {
    this.cfgBeans.forEach(callback);
  }

  /** 当前容器是否已加载 */
  isLoaded(): boolean {
    return this.loaded;
  }

  /** 字段元数据快捷访问 */
  get fields(): readonly FieldMeta[] {
    return this.beanClass.FIELDS;
  }
}
