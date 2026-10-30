{
  "name": "configtool-config",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "description": "配置表工具生成的TypeScript配置读取代码(数据源: json)",
  "generator": "com.eouna.configtool.generator.template.ts.TsTemplateGenerator",
  "author": "CCL",
  "date": "${date}",
  "scripts": {
    "build": "tsc -p .",
    "start": "node dist/debug/main.js"
  },
  "devDependencies": {
    "typescript": "^5.9.3"
  }
}
