<h1 align="center">Config Table Tool</h1> 
<img alt="logo" src="configtool-app/src/main/resources/com/eouna/configtool/icon/main.png">

[English](README_EN.md) | [简体中文](README.md)

# GameConfigTool

Exports Excel game configuration tables into **data-reading code** and **data files** for several languages. Built on JavaFX with a visual interface, it supports exporting a single table or many tables in batch, and validates the table format and data while generating.

# Supported Languages

| Language | `--lang` value | Notes |
|------|------|------|
| Java | `java` | Default export language |
| JSON | `json` | Table data as JSON; non-GUI mode always appends it to guarantee a full validation pass |
| Go | `go` | |
| Rust | `rust` | |
| C# | `csharp` | |
| Lua | `lua` | |
| C++ | `cpp` | |

Multiple languages can be selected at once, and the output is written into per-language directories.

# Advantages

1. **Simple** - Export with a single click in the UI, or a single command from the CLI. The generated code ships with a data manager, so reading configuration is straightforward.
2. **Flexible** - 7 languages (with multi-language export at once), split tables, client/server per-endpoint export, constant configuration, Excel directory mapping and pre-checks cover many project scenarios.
3. **Designer-friendly** - Configure directly in Excel; the description row, type row and data-range row are clear and consistent. Format and data validation runs before generation, so problems surface early.

# Screenshots

1. Main window
   ![Main window](doc/img/MainView.png)
2. Settings
   ![Settings](doc/img/Setting.png)
3. Configuration guide
   ![Configuration guide](doc/img/Help.png)

# Documentation

- [Configuration Guide](doc/configuration_EN.md) - table layout rules, field types, validation rules, split tables, how to use the generated output in your project
- [Command Line Usage](doc/usage.md) - parameters, examples and exit codes for non-GUI mode (Chinese)

# TODO

~~1. Multi-language support (UI language extension and selection).~~
~~2. Command line support.~~
3. A display component for array configuration in the settings UI.