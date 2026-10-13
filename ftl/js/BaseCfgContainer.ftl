/**
 * 配置容器基类(数据源: json)
 *
 * @generator com.eouna.configtool.generator.template.js.JsTemplateGenerator
 * @author CCL
 * @date ${date}
 */

/**
 * 配置容器基类: 负责从json加载数据并按主键建立索引
 */
export class BaseCfgContainer {
  /**
   * @param {Function} beanClass 配置bean类(需提供 SHEET_NAME / ID_NAME)
   */
  constructor(beanClass) {
    this.beanClass = beanClass;
    /** @type {Map<any, any>} 主键 -> 配置bean */
    this.cfgBeanMap = new Map();
    /** @type {Array<any>} 配置bean列表 */
    this.cfgBeans = [];
    this.loaded = false;
  }

  /** 配置表名(工作薄名) */
  get sheetName() {
    return this.beanClass.SHEET_NAME;
  }

  /**
   * 从数据源加载本表配置
   *
   * @param {import('./JsonLoader.js').JsonLoader} loader 数据源
   * @returns {Promise<this>}
   */
  async load(loader) {
    const raw = await loader.loadSheet(this.sheetName);
    return this.loadFromObject(raw);
  }

  /**
   * 直接使用一个json对象填充容器(便于测试或自定义数据源)
   *
   * @param {object} raw { 主键: { 字段: 值 } }
   * @returns {this}
   */
  loadFromObject(raw) {
    const map = new Map();
    if (raw && typeof raw === 'object') {
      for (const [key, value] of Object.entries(raw)) {
        if (key === '0') {
          // json中key为0的是枚举定义, 不是数据行
          continue;
        }
        this.validateBeanFields(value);
        const bean = new this.beanClass(value);
        const id = bean.getId();
        map.set(id === undefined || id === null ? key : id, bean);
      }
    }
    this.cfgBeanMap = map;
    this.cfgBeans = Array.from(map.values());
    this.loaded = true;
    return this;
  }

  /** 按字段 rawType 校验 List/Set/Map 的数量限制 */
  validateBeanFields(raw) {
    if (!raw || typeof raw !== 'object') {
      return;
    }
    for (const field of this.beanClass.FIELDS || []) {
      if (Object.prototype.hasOwnProperty.call(raw, field.name)) {
        this.validateFieldValue(field.rawType, raw[field.name], field.name);
      }
    }
  }

  /** 递归校验集合类型，限制值语义与 Java 保持一致: 数量必须严格等于限制值 */
  validateFieldValue(rawType, value, fieldName) {
    const raw = (rawType || '').trim();
    const seqMatch = raw.match(/^(list|set)<(.*)>\{(.)(\d*)\}$/i);
    if (seqMatch) {
      if (!Array.isArray(value)) {
        return;
      }
      this.checkSizeLimit(value.length, seqMatch[4], fieldName);
      for (const item of value) {
        this.validateFieldValue(seqMatch[2], item, fieldName);
      }
      return;
    }

    const mapMatch = raw.match(/^map<(.*?)\{(.)\}(.*)>\{(.)(\d*)\}$/i);
    if (mapMatch && value && typeof value === 'object' && !Array.isArray(value)) {
      const keys = Object.keys(value);
      this.checkSizeLimit(keys.length, mapMatch[5], fieldName);
      for (const key of keys) {
        this.validateFieldValue(mapMatch[3], value[key], fieldName);
      }
    }
  }

  /** 校验集合数量限制 */
  checkSizeLimit(actualSize, limitText, fieldName) {
    const limit = Number(limitText || '0');
    if (limit > 0 && actualSize !== limit) {
      throw new Error(
          this.sheetName + ' 字段 ' + fieldName + ' 对应的数据数量: ' + actualSize
          + ' 与限制值: ' + limit + ' 不一致');
    }
  }

  /**
   * 按主键获取配置(数字与字符串主键均可)
   *
   * @param {any} id 主键
   * @returns {any|undefined}
   */
  getById(id) {
    if (this.cfgBeanMap.has(id)) {
      return this.cfgBeanMap.get(id);
    }
    const num = Number(id);
    if (!Number.isNaN(num) && this.cfgBeanMap.has(num)) {
      return this.cfgBeanMap.get(num);
    }
    return undefined;
  }

  /** 按主键获取配置(同 getById) */
  get(id) {
    return this.getById(id);
  }

  /** 是否包含指定主键 */
  has(id) {
    return this.getById(id) !== undefined;
  }

  /** 获取全部配置 */
  getAll() {
    return this.cfgBeans;
  }

  /** 配置条数 */
  get size() {
    return this.cfgBeanMap.size;
  }

  /** 遍历全部配置 */
  forEach(callback) {
    this.cfgBeans.forEach(callback);
  }
}
