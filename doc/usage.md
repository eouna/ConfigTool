# 配置表工具 用法说明

配置表工具基于 JavaFX 实现, 用于将 excel 配置表导出为多种语言的读取代码与数据文件.

支持两种启动方式:

| 方式        | 说明                                   |
|-----------|--------------------------------------|
| GUI(图形界面) | 默认方式, 通过界面选择 excel 目录/导出目录/导出语言后点击生成 |
| 非GUI(命令行) | 适合脚本/CI/批量调用, 传入参数后自动生成并退出           |

---

## 一、图形界面(GUI)

1. 运行 `ConfigTool.exe`(或 `java -jar ...` / IDEA 中启动 `ConfigToolGenApplication`), 不传 `--mode` 时即为 GUI 模式.
2. 在界面上选择 excel 目录与模板导出目录.
3. 勾选需要导出的语言(可多选): `java` / `json` / `go` / `rust` / `csharp` / `lua` / `cpp`.
4. 点击生成, 生成结果与日志显示在主界面日志区.

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
| `--help` / `--usage` / `-h` | 否  | 打印用法说明并退出                                  | -                 |

`--lang` 可选值: `java` / `json` / `go` / `rust` / `csharp` / `lua` / `cpp`.

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
```

### 2.4 非GUI模式约定

1. **固定追加 json**: 非GUI模式在指定语言之外, 会固定追加 `json` 生成器, 保证至少完整走一次配置检查逻辑(字段类型、枚举、重复工作薄名等校验). 因此 `--lang=go` 实际会生成 `go` + `json`.
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

生成后按语言分目录存放, 例如 `--lang=java,go --dest_path=./out`:

```
out/
├── java/       # java bean / container / manager 等
├── go/         # go bean / container / manager 等
└── json/       # 每张表对应的 json 数据(非GUI模式自动追加)
```

说明:

1. 每种语言的 bean / container 等文件放在各自的子目录中.
2. `--excel_path` 指向目录时会收集该目录下所有 `.xlsx` 文件; 指向单个文件时只生成该文件.
3. 分表(表名包含 `_`)会自动合并生成总表与子表.

---

## 四、源码方式运行(开发)

```shell
# 编译
mvn -o clean compile

# 打包可执行 fat jar
mvn -o -pl configtool-app -am package -Dexec.skip=true

# 以非GUI方式运行(需要 JavaFX 模块)
java --module-path <javafx-jars> --add-modules=javafx.controls,javafx.fxml \
     -cp configtool-app/target/configtool-app-1.0.5.jar \
     com.eouna.configtool.Launcher --mode=none_gui --excel_path=./example --dest_path=./out --lang=go
```

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

脚本会在缺少 JDK(含 jpackage/jlink) 时自动下载便携 JDK, 并使用 Windows 自带的 `tar` / `iexpress` 完成打包, 无需额外第三方工具.
