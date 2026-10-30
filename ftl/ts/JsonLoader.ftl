import type { JsonRecord } from "./Types.js";
import { isJsonRecord } from "./Types.js";

/**
 * 配置数据加载器(数据源: json)
 *
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 *
 * 说明: 工具当前的json导出方式为 <#if isSplitMultiJson>每张表一个json文件(<表名>.json)<#else>所有表合并为 out.json</#if>,
 *       容器只依赖 loadSheet(sheetName) 取数, 两种导出方式都已兼容.
 */

/** 工具当前的json导出方式: true=每张表一个json文件, false=所有表合并为 out.json */
export const SPLIT_MULTI_JSON = ${isSplitMultiJson?c};

export abstract class JsonLoader {
  /** 读取并解析一个json文件 */
  abstract load(fileName: string): Promise<unknown>;

  /** 读取一张配置表的数据, 统一返回 { 主键: { 字段: 值 } } */
  async loadSheet(sheetName: string): Promise<JsonRecord> {
    let sheetJson: unknown;
    if (SPLIT_MULTI_JSON) {
      sheetJson = await this.load(sheetName + ".json");
    } else {
      const mergedJson = await this.load("out.json");
      sheetJson = isJsonRecord(mergedJson) ? mergedJson[sheetName] : undefined;
    }
    if (!isJsonRecord(sheetJson)) {
      return {};
    }
    const data = sheetJson["data"];
    return isJsonRecord(data) ? data : {};
  }
}

/** Node环境加载器: 读取本地目录下的json文件 */
export class FileJsonLoader extends JsonLoader {
  private readonly rootPath: string;

  constructor(rootPath: string) {
    super();
    this.rootPath = rootPath || ".";
  }

  async load(fileName: string): Promise<unknown> {
    const fs = await import("node:fs/promises");
    const path = await import("node:path");
    const fullPath = path.join(this.rootPath, fileName);
    const text = await fs.readFile(fullPath, "utf8");
    return JSON.parse(text) as unknown;
  }
}

/** 浏览器/小游戏加载器: 通过fetch读取json文件 */
export class FetchJsonLoader extends JsonLoader {
  private readonly baseUrl: string;

  constructor(baseUrl: string = "") {
    super();
    this.baseUrl = baseUrl;
  }

  async load(fileName: string): Promise<unknown> {
    const base = this.baseUrl ? this.baseUrl.replace(/\/+$/, "") + "/" : "";
    const url = base + fileName;
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error("加载配置失败: " + url + " (http " + response.status + ")");
    }
    return (await response.json()) as unknown;
  }
}

/** 内存加载器: 单元测试或自定义数据源时使用 */
export class MemoryJsonLoader extends JsonLoader {
  private readonly dataMap: Readonly<Record<string, unknown>>;

  constructor(dataMap: Readonly<Record<string, unknown>> = {}) {
    super();
    this.dataMap = dataMap;
  }

  async load(fileName: string): Promise<unknown> {
    if (!Object.prototype.hasOwnProperty.call(this.dataMap, fileName)) {
      throw new Error("内存数据源中没有配置: " + fileName);
    }
    return this.dataMap[fileName];
  }
}
