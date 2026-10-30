# 配置表工具 用法说明

配置表工具基于 JavaFX 实现, 用于将 excel 配置表导出为多种语言的读取代码与数据文件.

支持两种启动方式:

| 方式        | 说明                                   |
|-----------|--------------------------------------|
| GUI(图形界面) | 默认方式, 通过界面选择 excel 目录/导出目录/导出语言后点击生成 |
| 非GUI(命令行) | 适合脚本/CI/批量调用, 传入参数后自动生成并退出           |

目录:

1. [配置表字段填写指南(策划向)](#配置表字段填写指南策划向)
2. [图形界面(GUI)](#一图形界面gui)
3. [非GUI(命令行)模式](#二非gui命令行模式)
4. [生成结果目录结构](#三生成结果目录结构)
5. [源码方式运行(开发)](#四源码方式运行开发)
6. [打包为免安装程序](#五打包为免安装程序)

---

# 配置表字段填写指南(策划向)

> 本节面向**填表的策划**, 只讲"每个格子该怎么写". 工具内部的类型映射等技术细节见 [配置说明](configuration.md).

## 0. 一张配置表长什么样

**一行数据 = 一个条目(通常是一行配置), 一列 = 一个字段.**

每张表的前 4 行是**表头**(行号可配置), 真正的数据从第 5 行开始:

| 行      | 作用          | 举例           | 谁来写   |
|--------|-------------|--------------|-------|
| 第 1 行  | 字段描述, 写给人看  | `整数展示列`      | 策划    |
| 第 2 行  | 字段类型, 给工具用  | `int`        | 策划/程序 |
| 第 3 行  | 字段名, 给程序代码用 | `intExample` | 程序    |
| 第 4 行  | 数据范围, 可留空   | `client`     | 程序    |
| 第 5 行起 | 真正的配置数据     | `100`        | 策划    |

三条硬性规则:

1. **第一列必须是 id 列**, 列名默认 `id`.
2. 工具**只读 excel 的第一个工作薄**(常量写在名为 `constant` 的工作薄里, 见 [6. 常量配置](#6-常量配置)).
3. 字段名不能以数字或特殊符号开头(`_` 除外), 同一个工作薄里不要重名.

## 1. 基础类型: 一个格子写一个值

| 类型写法          | 含义             | 怎么填                      | 示例                    |
|---------------|----------------|--------------------------|-----------------------|
| `int`         | 整数(常用)         | 直接写数字; 写小数会**向下取整**      | `100`, `200.01` → 200 |
| `string`      | 字符串            | 直接写文字, 不用加引号             | `测试行1`                |
| `bool`/`Bool` | 布尔             | `true` / `false`(不区分大小写) | `true`                |
| `float`       | 单精度小数          | 数字, 建议 7~8 位有效数字         | `100.11`              |
| `double`      | 双精度小数          | 数字, 16~17 位有效数字          | `200.00001`           |
| `byte`        | 单字节整数 -128~127 | 小整数                      | `0`                   |
| `short`       | 短整型 ±3 万左右     | 数字                       | `100`                 |
| `long`        | 长整型            | 大数字                      | `10000000000`         |
| `Date<格式>`    | 时间             | 按 `<>` 里写的格式填            | 见下                    |

时间类型: 格式写在尖括号里, 填的内容必须和它完全一致.

| 类型行写法                    | 数据写法               |
|--------------------------|--------------------|
| `Date<yyyy-MM-dd HH:mm>` | `2023-12-14 19:01` |
| `Date<yyyy-MM-dd>`       | `2023-12-14`       |

## 2. 枚举: 只能填列出来的那几个值

写法: `枚举名(值1,值2,值3)` — 括号里把**所有允许的取值**列出来.

| 位置  | 内容                                 |
|-----|------------------------------------|
| 类型行 | `EnumExample(ceshi,ceshi1,ceshi2)` |
| 数据行 | `ceshi1` (只能从上面三个里选)               |

说明:

1. 枚举名(如 `EnumExample`)是给程序用的, 建议和程序约定好.
2. 数据填了括号里没有的值, 生成时会报错.
3. `枚举名()` 这种空括号写法表示"只引用这个枚举类型, 不重新声明取值", 常配合列表使用(见下).

## 3. 列表 / 集合: 一个格子里写多个值

写法: `List<子类型>{分隔符}`, 其中 `{}` 里的**分隔符只能是一个字符**, 由你自己定(常用 `,` 或 `;`).

| 类型行写法                    | 含义           | 数据写法            |
|--------------------------|--------------|-----------------|
| `List<int>{,}`           | 整数列表         | `100,101,102`   |
| `List<string>{;}`        | 字符串列表        | `装备;道具;材料`      |
| `Set<int>{,}`            | 不重复列表        | `100,200,300`   |
| `List<EnumExample()>{,}` | 枚举列表(引用已有枚举) | `ceshi1,ceshi2` |

要点:

1. `Set`(不重复列表)里**写了重复值会直接报错**, `List` 允许重复.
2. 列表可以套列表: `List<List<int>{,}>{;}` 表示"外层用 `;` 分隔, 内层用 `,` 分隔".
    - 数据 `100,101;200;301,302,30` → 结果是 `[[100,101], [200], [301,302,30]]`
3. 不同类型里用到的分隔符不能重复, 否则会报"列表或Map分隔符使用错误".

## 4. 键值对: 一个格子里写多组"键: 值"

写法: `Map<键类型{键值分隔符}值类型>{条目分隔符}`

| 类型行写法                           | 含义         | 数据写法                          | 结果                                                 |
|---------------------------------|------------|-------------------------------|----------------------------------------------------|
| `Map<int{,}String>{;}`          | 整数 → 字符串   | `100,101a;200,100b;301,302`   | `{100:"101a", 200:"100b", 301:"302"}`              |
| `Map<int{,}List<String>{-}>{;}` | 整数 → 字符串列表 | `100,101a;200,100b-d;301,302` | `{100:["101a"], 200:["100b","100d"], 301:["302"]}` |

要点:

1. 两组分隔符要分别写清楚: `{键值分隔符}` 和 `{条目分隔符}`.
2. **键(Key)只能用基础类型, 且不能用布尔**(`bool`), 否则直接报错.
3. 值可以继续套列表、枚举等: `Map<int{,}List<String>{-}>{;}`.

## 5. 数量限制: 最多能填几个

在分隔符后面直接跟一个数字, 表示**最多几个元素**, 超出会报错.

| 类型行写法                   | 含义           |
|-------------------------|--------------|
| `List<int>{,5}`         | 整数列表必须为 5 个  |
| `Set<int>{,5}`          | 不重复列表必须为 5 个 |
| `Map<int{,}String>{;2}` | 键值对必须为 2 组   |

> 数据数量与限制不一致时报错: `字段对应的数据数量: 1 与限制值: 2 不一致`.

## 6. 常量配置

`.xlsx` 里除了数据工作薄, 还可以放一个名为 **`constant`**(名字可在设置中改)的工作薄, 用来写全局常量. 它的列顺序和普通数据表
**不一样**:

| 名字列           | 类型列            | 值           | 描述        |
|---------------|----------------|-------------|-----------|
| `IntTest`     | `int`          | `100`       | int常量测试   |
| `IntListDemo` | `List<int>{,}` | `100300500` | int常量列表测试 |

即: **第 1 列常量名, 第 2 列类型, 第 3 列值, 第 4 列描述**.

常量与数据源的关系:

- 选择 `excel` 数据源: 生成的代码在运行时读取 excel 里的该工作薄;
- 选择 `json` 数据源: 常量会随该表的 json 一起导出到 `const` 段(按 excel 行列布局, 首行为表头, 单元格文本与 excel 一致),
  java / go / csharp / rust / cpp 的 json 数据源都会从 `const` 还原常量, 不再依赖 excel;
- `js` / `lua`: 常量在生成代码时就写入 `constants_*` 模块, 与选择哪种数据源无关.

## 7. 数据范围: 这个字段给谁用

表头第 4 行(数据范围行)一般**留空**, 表示客户端和服务端都要. 也可以填写:

| 填什么      | 含义                      |
|----------|-------------------------|
| 留空       | 客户端 + 服务端都使用            |
| `client` | 仅客户端使用(服务端代码里不生成这个字段)   |
| `server` | 仅服务端使用(客户端 json 数据里不导出) |

> 这两个关键字可以在设置界面或 `ToolConfig.yaml` 里改:
> `excelConf.dataRangeClientSkipStr`(客户端字段名, 客户端导出时跳过该名字的字段)、
> `excelConf.dataRangeServerSkipStr`(服务端字段名, 服务端导出时跳过该名字的字段).

导出时按界面「导出选项」、命令行 `--export_mode=client|server` 或 `ToolConfig.yaml` 的 `exportConf.exportMode` 决定哪些字段进入生成结果:

| 导出选项 | 效果                                              |
|------|-------------------------------------------------|
| 客户端  | 数据范围行为 `dataRangeClientSkipStr`(默认 `server`)的字段**不导出** |
| 服务端  | 数据范围行为 `dataRangeServerSkipStr`(默认 `client`)的字段**不导出** |

> 例: `example.xlsx` 中 `byteExample` 标记为 `client`、`floatExample` 标记为 `server`:
> 客户端导出只生成 `byteExample`, 服务端导出只生成 `floatExample`.
> 把这两个名字改成自定义值后, 生成期过滤与生成代码运行期都按新名字比较(不会写死).

## 8. 留空的格子会怎样

留空表示"用默认值": 数字类型 → `0`, 布尔 → `false`, 列表/集合/键值对 → 空, 字符串/时间/枚举 → 空.

## 9. 常见报错对照表

| 工具报错                                | 通常是哪里写错了              |
|-------------------------------------|-----------------------|
| 字段类型: xxx 找不到匹配项                    | 第 2 行类型写错了(拼写/大小写/符号) |
| 时间格式: yyyy-MM-dd HH:mm 和数据源: xx 不匹配 | 时间写法和 `<>` 里的格式对不上    |
| 字段对应的数据数量: 1 与限制值: 2 不一致            | 元素个数必须严格等于 `{分隔符+数量}` |
| Set列数据出现重复数据: xxx                   | 不重复列表里写了重复值           |
| 列表或Map分隔符使用错误, 出现重复的分隔符: x          | 同一个类型里两个 `{}` 用了同一个字符 |
| 解析类型错误Map类型字段的key值无法使用布尔类型          | Map 的键用了 `bool`       |
| Map类型解析错误, key不为基础类型                | Map 的键用了列表/枚举等        |
| 字段名: xxx 不符合规则, 不能以数字或者特殊符号开头       | 第 3 行字段名起头不合法         |
| 拥有重复的工作薄名: xxx                      | 两个 excel 的 sheet 名重名  |

## 10. 完整示例

项目里放了一份可直接打开的示例表 [example.xlsx](../example/example.xlsx), 覆盖了上面所有类型. 下面是从中摘录的一部分:

| 第 1 行(描述)  | 第 2 行(类型)                          | 第 3 行(字段名)             | 第 5 行(数据)                     |
|------------|------------------------------------|------------------------|-------------------------------|
| id         | `int`                              | `id`                   | `1`                           |
| 名称         | `string`                           | `name`                 | `测试行1`                        |
| 整数展示列      | `int`                              | `intExample`           | `100`                         |
| 浮点数展示列     | `float`                            | `floatExample`         | `100.11`                      |
| 枚举展示列      | `EnumExample(ceshi,ceshi1,ceshi2)` | `enumExample`          | `ceshi1`                      |
| 枚举展示列      | `List<EnumExample()>{,}`           | `listEnumExample`      | `ceshi1,ceshi2`               |
| 列表展示列      | `List<int>{,}`                     | `IntlistExample`       | `100,101,102`                 |
| 列表嵌列表展示列   | `List<List<int>{,}>{;}`            | `List2IntExample`      | `100,101;200;301,302,30`      |
| 键值展示列      | `Map<int{,}String>{;}`             | `IntStrMap`            | `100,101a;200,100b;301,302`   |
| 键值展示列      | `Map<int{,}List<String>{-}>{;}`    | `MapListStringExample` | `100,101a;200,100b-d;301,302` |
| Map长度限制展示列 | `Map<int{,}String>{;2}`            | `MapLenLimitExample`   | `100,101a;200,100b`           |
| Set展示列     | `Set<int>{,}`                      | `SetExample`           | `100,200,300`                 |
| 时间展示列      | `Date<yyyy-MM-dd HH:mm>`           | `DateExampleShow`      | `2023-12-14 19:01`            |

---

## 一、图形界面(GUI)

1. 运行 `ConfigTool.exe`(或 `java -jar ...` / IDEA 中启动 `ConfigToolGenApplication`), 不传 `--mode` 时即为 GUI 模式.
2. 在界面上选择 excel 目录与模板导出目录.
3. 勾选需要导出的语言(可多选): `java` / `json` / `go` / `rust` / `csharp` / `lua` / `cpp` / `js` / `ts`
   (勾选 js/ts 或数据源选择 json 时会自动带上 json).
4. 第一步右下角有**数据加载选项**(`excel` / `json`)与**导出选项**(`客户端` / `服务端`)两个小模块:
   前者决定生成的代码读 excel 还是 json; 后者决定导出哪些字段(见 [7. 数据范围](#7-数据范围-这个字段给谁用)).
5. 点击生成, 生成结果与日志显示在主界面日志区.
6. 菜单「帮助 → 检查更新」可手动检查版本; 启动时也会静默检查一次(见 [六、版本检查](#六版本检查)).

---

## 二、非GUI(命令行)模式

### 2.1 启动方式

```
ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out --lang=go
```

`--mode` 兼容写法: `none_gui` / `nogui` / `commandline` / `command_line` / `cli` / `console`;
`gui` / `default` / `fx` 表示图形界面(GUI 为默认值).

### 2.2 参数说明

| 参数                          | 必填 | 说明                                         | 默认值               |
|-----------------------------|:--:|--------------------------------------------|-------------------|
| `--mode=gui\|none_gui`      | 否  | 启动模式                                       | `gui`             |
| `--excel_path=<路径>`         | 否  | excel 目录或单个 excel 文件, **支持相对路径**(相对当前工作目录) | 读取系统配置中的 excel 路径 |
| `--dest_path=<路径>`          | 否  | 模板导出目录, **支持相对路径**(相对当前工作目录)               | 读取系统配置中的导出路径      |
| `--lang=<语言列表>`             | 否  | 导出语言, 多个语言用英文逗号分隔                          | `java`            |
| `--load_source=<方式>`        | 否  | 数据加载选项: `excel` / `json`                     | `excel`           |
| `--export_mode=<模式>`        | 否  | 导出选项: `client`(客户端导出) / `server`(服务端导出)    | 读取配置文件中的导出模式      |
| `--help` / `--usage` / `-h` | 否  | 打印用法说明并退出                                  | -                 |

`--lang` 可选值: `java` / `json` / `go` / `rust` / `csharp` / `lua` / `cpp` / `js` / `ts`.

> `js` / `ts` 模板的读取源是 json 数据, 需与 `json` 一起导出(非GUI模式会自动追加 json, 不会缺).
> 即使传入 `--load_source=excel`, 包含 `js` / `ts` 的语言组合也会自动改用 json.

> 注意: 参数必须使用 `--key=value` 形式(例如 `--excel_path=./example`), 不支持 `--excel_path ./example` 这种空格分隔写法.

### 2.3 用法示例

```shell
# 1. 打印用法说明
ConfigTool.exe --help

# 2. 不指定语言: 默认导出 java(非GUI模式会自动追加 json)
ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out

# 3. 同时导出 go 与 rust
ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out --lang=go,rust

# 4. 只导出单个 excel 文件
ConfigTool.exe --mode=none_gui --excel_path=./example/example.xlsx --dest_path=./out --lang=csharp

# 5. 使用绝对路径
ConfigTool.exe --mode=none_gui --excel_path=D:\config\excel --dest_path=D:\config\out --lang=lua

# 6. 服务端导出(不导出数据范围行为 client 的字段)
ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out --lang=go --export_mode=server

# 7. json 数据加载 + 客户端导出
ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out --lang=java --load_source=json --export_mode=client

# 8. 同时导出 Java 与 TypeScript(自动选择两者共同支持的 json)
ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out --lang=java,ts
```

### 2.4 非GUI模式约定

1. **固定追加 json**: 非GUI模式在指定语言之外, 会固定追加 `json` 生成器, 保证至少完整走一次配置检查逻辑(
   字段类型、枚举、重复工作薄名等校验). 因此 `--lang=go` 实际会生成 `go` + `json`.
2. **不写回配置**: 命令行传入的 `excel_path` / `dest_path` 只在本次运行生效, 不会写回系统配置文件, 不会覆盖界面上的配置.
3. **导出目录会被清理**: 每次生成前会清空导出目录, 请勿将导出目录指向需要保留其他文件的目录.
4. **退出码**:

   | 退出码 | 含义                |
            |:---:|-------------------|
   |  0  | 生成成功              |
   |  1  | 生成失败(校验不通过/解析异常等) |
   |  2  | 未找到 excel 文件      |

5. **日志**: 控制台会输出生成进度与结果; 同时写入 `log/ConfigTool.log`.

---

## 三、生成结果目录结构

生成后按语言分目录存放, 例如 `--lang=java,go,ts --dest_path=./out`:

```
out/
├── java/       # java bean / container / manager 等
├── go/         # go bean / container / manager 等
├── js/         # javascript 读取代码(数据源为 json)
├── ts/         # typescript 读取代码(数据源为 json)
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
└── json/       # 每张表对应的 json 数据(结构: { const: [常量行], data: { id: {字段: 值} } })
```

说明:

1. 每种语言的 bean / container 等文件放在各自的子目录中.
2. `--excel_path` 指向目录时会收集该目录下所有 `.xlsx` 文件; 指向单个文件时只生成该文件.
3. 分表(表名包含 `_`)会自动合并生成总表与子表.
4. `jsonTemplateConf.isSplitMultiJson=true` 时 JSON 位于 `json/`; 设为 `false` 时合并为导出目录根部的 `out.json`.

---

## 三之二、数据加载选项(excel / json)与导出选项(client / server)

界面「数据加载选项」或命令行 `--load_source=excel|json` 决定生成代码读取哪种数据：

| 数据源     | 说明                                                                                               |
|---------|--------------------------------------------------------------------------------------------------|
| `excel` | 生成的代码直接读 xlsx(需要 xlsx 解析库, 如 java 的 POI)                                                         |
| `json`  | 生成的代码读取 `<导出目录>/json/<表名>.json`(结构 `{ const: [...], data: {...} }`); 选择 json 数据源时工具会自动追加 json 导出 |

每种语言支持的数据加载方式统一配置在
[`LanguageDataLoadConfig`](../configtool-app/src/main/java/com/eouna/configtool/generator/LanguageDataLoadConfig.java):

| 语言 | Excel | JSON |
|------|:-----:|:----:|
| `java` / `go` / `rust` / `csharp` / `lua` / `cpp` | 支持 | 支持 |
| `json` / `js` / `ts` | 不支持 | 支持 |

> 选择 `json` 数据源时, 分表模式的主入口默认读取 `<导出目录>/json`, 合并模式默认读取 `<导出目录>/out.json`,
> 因此可以直接运行主入口完成预加载, 也可以由预加载流程显式传入同一目录。

界面「导出选项」或 `ToolConfig.yaml` 的 `exportConf.exportMode` 决定导出哪些字段(支持全部语言模板):

| 取值       | 含义                                                          |
|----------|-------------------------------------------------------------|
| `client` | 客户端导出(默认): 数据范围行为 `server` 的字段不导出(不生成字段/不写入json数据)        |
| `server` | 服务端导出: 数据范围行为 `client` 的字段不导出                              |

> 该字段与界面「导出选项」双向关联: 界面上切换会写回 `ToolConfig.yaml`, 配置文件改动会在下次启动时反映到界面;
> 导出模式变化后需要重新生成模板(第一步)与预加载(第二步).

**json 数据源的已知限制**（重要）：

1. **Map 的 key 必须唯一**。excel 中同一行 Map 允许写重复 key（如 `200,201b;200,100b`），但 json 对象的 key 天然唯一，重复
   key 会被合并 → 回读时数据变少。工具现在会在解析时**直接报错**：`Map类型字段出现重复的key: xxx`，请把重复 key 改成不同
   key。
2. 列表/集合会**按类型里的分隔符重新拼回字符串**再解析（`List<int>{,}` → `100,101,102`；`Set<int>{,}` 同理），因此集合的"
   顺序"不保证与原 excel 一致。
3. 时间类型按类型里写的格式还原（`Date<yyyy-MM-dd HH:mm>` → `2023-12-14 19:01`）。
4. 每张表的 json 分为两段: `const`(该表所在 excel 文件里常量表的行, 按 excel 行列布局的字符串二维数组, 首行为表头)与
   `data`(excel 里的配置数据, 与旧版 json 内容一致)。java / go / csharp / rust / cpp 的 json 数据源会分别读取这两段;
   没有常量时 `const` 为空数组(`js` / `ts` / `lua` 的常量写在生成代码里)。

TypeScript 输出可直接构建和运行:

```shell
cd out/ts
npm install
npm run build
npm start
```

`ts/src/JsonLoader.ts` 同时提供 Node 文件、浏览器 `fetch` 和内存数据三种加载器; 分表与 `out.json` 合并模式均兼容.

## 四、源码方式运行(开发)

```shell
# 编译
mvn -o clean compile

# 打包可执行 fat jar
mvn -o -pl configtool-app -am package -Dexec.skip=true

# 以非GUI方式运行(需要 JavaFX 模块)
java --module-path <javafx-jars> --add-modules=javafx.controls,javafx.fxml \
     -cp configtool-app/target/configtool-app-1.0.10.jar \
     com.eouna.configtool.Launcher --mode=none_gui --excel_path=./example --dest_path=./out --lang=go
```

### 开发约定(单例统一走容器)

1. 新增的单例类**统一用 `@Component` 注册到容器**, 使用时用 `@AutoInject` 注入, **不要再新增静态单例**(enum
   Singleton、static INSTANCE 等)。
2. 需要在启动时做一次初始化的单例, 在 `ConfigToolAppEntrance#run` 里触发一次(例如 `templateEnvContext.refresh()`)。
3. 参考示例:
    - `com.eouna.configtool.env.TemplateEnvContext`(各语言模板运行环境的检测结果, 内存缓存)
    - `com.eouna.configtool.env.TemplateEnvChecker`(环境检测实现)
    - `com.eouna.configtool.configholder.SystemConfigHolder`(系统配置, 同样由容器管理)

### 配置回存与AspectJ织入

界面改配置后会自动回存 `ToolConfig.yaml`, 由切面 `ConfigDataUpdateAspect`(配置bean的setter)完成。
织入统一用官方维护的 `aspectj-maven-plugin`(1.16.1)做**构建期织入**, 运行期不需要agent:

| 运行方式 | 织入方式 |
|------|--------------------------------------------------------------|
| `mvn compile` / `mvn package` / 发布包 | 插件在 `compile` 阶段用ajc织入; `forceAjcCompile=true` 保证即使 `target/classes` 里是IDEA(javac)编译出的未织入class, 也会被重新织入 |
| IDEA 里运行 | IDEA的Maven集成会识别 `aspectj-maven-plugin`, 该模块直接用Ajc编译(自动织入); 若某个IDEA版本未生效, 可给运行配置加 `-javaagent:templatelib/aspectjweaver-1.9.24.jar`(载入期织入), 或在 Settings → Build, Execution, Deployment → Compiler → Java Compiler 里选择 `Ajc` |

> 排查时可加 `-Daspectj.skip=true` 临时跳过织入(仅用于对照; 跳过时配置变更不会自动回存).
> 打包用的精简运行库保留 `java.instrument`, 以便需要时用 `-javaagent` 做载入期织入; 发布包默认走构建期织入, 不需要agent.

---

## 四之二、配置文件(ToolConfig.yaml)

系统配置为 `config/ToolConfig.yaml`(旧版本名为 `ExcelConfig.yaml`, **不再兼容旧文件名**, 请直接使用新名字),
主要包含: `excelConf`(字段行列/路径/常量工作薄名/数据范围跳过字段名)、`javaTemplateConf`(包名/数据管理类名)、
`exportConf`(导出模式: 客户端/服务端)、`jsonTemplateConf`、`commonTemplateConf`、`syncConfig`、`cache` 等.

```yaml
excelConf:
  # 数据范围行中标识服务端字段的名字(可自定义), 服务端导出时跳过该名字的字段
  dataRangeServerSkipStr: client
  # 数据范围行中标识客户端字段的名字(可自定义), 客户端导出时跳过该名字的字段
  dataRangeClientSkipStr: server

exportConf:
  # client=客户端导出(跳过 dataRangeClientSkipStr 标记的字段); server=服务端导出(跳过 dataRangeServerSkipStr 标记的字段)
  exportMode: client

jsonTemplateConf:
  # true=每张表一个 json/<表名>.json; false=合并为导出目录根部的 out.json
  isSplitMultiJson: true
```

> 两个"跳过字段名"都可在设置界面或该配置文件里改成任意名字(例如 `c` / `s`, 或中文),
> 生成期过滤与生成代码运行期的跳过判断都按该名字比较, 不写死在代码里.


---

## 五、打包为免安装程序

项目提供一键打包脚本 `package.cmd` / `package.ps1`, 生成本地无需安装 Java 的绿色版与单文件版:

| 产物       | 路径                                    |
|----------|---------------------------------------|
| 绿色免解包目录版 | `dist\green\ConfigTool.exe`           |
| 单文件版     | `dist\single\ConfigTool-portable.exe` |

```cmd
package.cmd
```

脚本会在缺少 JDK(含 jpackage/jlink)时自动下载便携 JDK, 并使用 Windows 自带的 `tar` / `iexpress` 完成打包, 无需额外第三方工具.

---

## 六、版本检查

工具会从 github 仓库(`eouna/ConfigTool`)的 release 检查最新版本, 有两种触发方式:

| 触发方式          | 行为                                                       |
|---------------|----------------------------------------------------------|
| 启动时静默检查       | 后台线程请求最新 release, **10s 超时**; 超时/失败只记日志, 本次不再重试, 下次启动再检查 |
| 菜单「帮助 → 检查更新」 | 手动检查: 有新版本弹更新提示, 已是最新/失败给出提示                             |

发现新版本时的弹窗有两个操作:

- **更新**: 用系统浏览器打开该版本的 github release 页面;
- **跳过本次**: 记录该版本到 `config/VersionSkipConf.txt`, 之后**静默检查不再提示该版本**(菜单手动检查仍会提示)。

说明:

1. 每个进程只做一次静默检查; 未跟踪的 `config/VersionSkipConf.txt` 可手动删除以恢复提示.
2. 打包用的精简运行库必须包含 `jdk.crypto.ec`, 否则 HTTPS 握手失败(`SSLHandshakeException: handshake_failure`)导致检查更新不可用;
   `package.ps1` 已包含该模块.
