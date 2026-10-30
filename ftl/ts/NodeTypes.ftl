/**
 * 生成工程只依赖Node内置模块, 不引入@types/node, 保证npm安装后可直接编译。
 *
 * @generator com.eouna.configtool.generator.template.ts.TsTemplateGenerator
 * @author CCL
 * @date ${date}
 */

declare module "node:fs/promises" {
  export function readFile(path: string, encoding: "utf8"): Promise<string>;
}

declare module "node:path" {
  export function join(...paths: string[]): string;
  export function resolve(...paths: string[]): string;
  export function dirname(path: string): string;
}

declare module "node:url" {
  export function fileURLToPath(url: string | URL): string;
}

declare const process: {
  readonly argv: string[];
};
