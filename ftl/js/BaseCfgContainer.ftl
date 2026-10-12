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