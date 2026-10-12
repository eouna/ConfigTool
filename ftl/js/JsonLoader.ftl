/**
 * 配置数据加载器(数据源: json)
 *
 * @generator com.eouna.configtool.generator.template.js.JsTemplateGenerator
 * @author CCL
 * @date ${date}
 *
 * 说明: 工具当前的json导出方式为 <#if isSplitMultiJson>每张表一个json文件(<表名>.json)<#else>所有表合并为 out.json</#if>,
 *      容器只依赖 loadSheet(sheetName) 取数, 两种导出方式都已兼容.
 */

/** 工具当前的json导出方式: true=每张表一个json文件, false=所有表合并为 out.json */
export const SPLIT_MULTI_JSON = ${isSplitMultiJson?c};

/**
 * 配置数据源基类: 只负责"按文件名取到一个json对象"
 */
export class JsonLoader {
  /**
   * 读取并解析一个json文件
   *
   * @param {string} fileName 例如 "Example.json"
   * @returns {Promise<object>}
   */
  async load(fileName) {
    throw new Error('JsonLoader.load 未实现: ' + fileName);
  }

  /**
   * 读取一张配置表的数据(兼容"分表多文件"与"合并单文件"两种导出方式)
   *
   * @param {string} sheetName 配置表名(工作薄名)
   * @returns {Promise<object>} { 主键: { 字段: 值 } }
   */
  async loadSheet(sheetName) {
    if (SPLIT_MULTI_JSON) {
      return await this.load(sheetName + '.json');
    }
    const all = await this.load('out.json');
    return (all && all[sheetName]) || {};
  }
}

/**
 * Node环境加载器: 读取本地目录下的json文件
 *
 * 用法: new FileJsonLoader('./out/json')
 */
export class FileJsonLoader extends JsonLoader {
  constructor(rootPath) {
    super();
    this.rootPath = rootPath || '.';
  }

  async load(fileName) {
    // 动态import, 避免浏览器环境静态引用node模块
    const fs = await import('node:fs/promises');
    const path = await import('node:path');
    const fullPath = path.join(this.rootPath, fileName);
    const text = await fs.readFile(fullPath, 'utf8');
    return JSON.parse(text);
  }
}

/**
 * 浏览器/小游戏加载器: 通过fetch读取json文件
 *
 * 用法: new FetchJsonLoader('http://localhost:8080/config/json')
 */
export class FetchJsonLoader extends JsonLoader {
  constructor(baseUrl) {
    super();
    this.baseUrl = baseUrl || '';
  }

  async load(fileName) {
    const base = this.baseUrl ? this.baseUrl.replace(/\/+$/, '') + '/' : '';
    const url = base + fileName;
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error('加载配置失败: ' + url + ' (http ' + response.status + ')');
    }
    return await response.json();
  }
}

/**
 * 内存加载器: 单元测试或自定义数据源时使用
 *
 * 用法: new MemoryJsonLoader({ 'Example.json': { '1': { id: 1 } } })
 */
export class MemoryJsonLoader extends JsonLoader {
  constructor(dataMap) {
    super();
    this.dataMap = dataMap || {};
  }

  async load(fileName) {
    if (!(fileName in this.dataMap)) {
      throw new Error('内存数据源中没有配置: ' + fileName);
    }
    return this.dataMap[fileName];
  }
}