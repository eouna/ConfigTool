<h1 align="center">Config Table Tool</h1> 
<img alt="logo" src="configtool-app/src/main/resources/com/eouna/configtool/icon/main.png">

[English](README_EN.md) | [简体中文](README.md)

# GameConfigTool

This tool exports Excel game configuration tables and generates code to read/write the data for several languages. It is built on JavaFX, so the visual interface is friendlier to both designers and programmers, and it supports exporting a single table or many tables in batch.

# Features

1. Generates code that loads Excel data in multiple languages, manages table data through a data manager, and provides a friendly way to read configuration data.
2. Validates the configuration table format and fields during generation, so problems are found early.
3. Supports uploading configuration tables: generate -> validate -> upload.
4. Uses multithreading to speed up template generation; data loading can also be accelerated.
5. Rich settings for different scenarios: reading by directory structure, keeping the Excel directory, and various optional configuration items.
6. Supports split tables (sub-tables).
7. Supports pre-checking, used for validating the data in configuration tables.
8. <span id="multiLan"></span> Supports exporting configuration data per endpoint (Client / Server); export restrictions can be controlled per field.
9. Supports constant configuration in configuration tables.

# Configuration Notes

1. Because the meaning of each Excel row differs between projects, the basic rows cannot be omitted. This tool requires four rows to describe a table: the field description row, the field type row, the field name row and the field data range row. Their row numbers are configurable.
2. The tool only reads the data of the first sheet in the Excel file, and the generated configuration bean is named after the sheet name plus `Cfg`. For example, if the sheet name is `Activity`, the generated configuration class is `ActivityCfg`; if the sheet name is already `ActivityCfg`, that name is used directly.
3. The first column of every configuration table must be the ID column, named `id`. The ID column name is configurable and defaults to `id`.

## Field Naming

1. A field name cannot start with a digit or other special characters, except `_`. Names may start with `_`, but this is not recommended.
2. Whether `_`-joined field names are converted to camelCase is configurable.

## Field Type Configuration

Common field types are supported. The same type is represented differently in different languages, and more types will be added later.

| Field | Representation | Value in the sheet | Range | Primitive |
|-------|-----------------------|:--------------------------------|-----------------------|:--------|
| Integer | java(int/Integer) | int/Int | ±2.1e9 | Yes |
| Byte | java(byte/Byte) | byte/Byte | -128-127 | Yes |
| Short | java(short/Short) | short/Short | -32768-32767 | Yes |
| Long | java(long/Long) | long/Long | ±92*10^17 | Yes |
| Boolean | java(boolean/Boolean) | boo/Bool/boolean/Boolean | true/false/TURE/FALSE | Yes |
| String | java(String) | string/String | | Yes |
| Float | java(float/Float) | float/Float | ±3.4028235E38 (7-8 significant digits) | Yes |
| Double | java(double/Double) | double/Double | ±1.79E+308 (16-17 significant digits) | Yes |
| List | java(List) | list/List | | No |
| Set | java(Set) | set/Set | | No |
| Map | java(Map) | map/Map | | No |
| Enum | java(Enum) | Written as ```EnumName(enumField,enumField)``` to represent an enum | | No |

more:

1. For how to configure the data of each field type, see (Help -> Configuration Guide) in the tool, which has more detailed explanations and examples.
2. The key of a map cannot be a non-primitive type or a boolean, otherwise an exception is thrown.
3. List and map types support nested structures of unlimited depth (more than three levels is not recommended).
4. List and map types support a length limit. It is configured after the last separator, in a form like ```{,10} the separator is , and the length is limited to 5 elements```.

## Validation Rules

1. The tool checks whether the data matches the configured field type.
2. For compatibility with int: if the value read for an int field is a floating-point number, it is rounded down.
3. The first column of every configuration table must be the ID column. Its format is not checked and its type may be omitted, defaulting to int.
4. The description row can be customized, but the last field-description row must be adjacent to the data row. See the example Excel for details.

## Split Tables

Split tables are distinguished by the Excel file name: if the name contains `_`, it is a split table. Currently only two levels are supported; multi-level split tables may be supported later. Tables are separated by `_`, and files whose names before `_` are identical are treated as one group of split tables.

**Note: field names must not be duplicated across the tables in one split group!**

### When to Use

1. A single table becomes too large.
2. The table data can be distinguished by some type.

### Generated Code Structure

If there are two sub-tables, three tables are generated: two sub-tables and one parent table. The parent table contains the data of all sub-tables, and each sub-table also contains its own data.

# Example Configuration

1. For reference, the project contains a basic Excel file that lists some basic usages. [reference document](example/example.xlsx)

# Excel Directory Mapping

The tool can map the configuration directory used by designers to the project directory used by the program. The configuration file is ```ExcelDirBindServerModuleConfLocal.txt```.

# UI

1. Main window
   ![Main window](doc/img/MainView.png)
2. Settings
   ![Settings](doc/img/Setting.png)
3. Configuration guide
   ![Configuration guide](doc/img/Help.png)

# Launch Arguments

Because reflection is used, some modules must be opened. Add the following launch arguments when starting the project:

```text
--add-opens
java.base/java.lang=ALL-UNNAMED
--add-opens
java.base/java.lang.invoke=ALL-UNNAMED
--add-opens
java.base/java.math=ALL-UNNAMED
--add-opens
java.base/java.util=ALL-UNNAMED
--add-opens
java.base/java.nio=ALL-UNNAMED
--add-opens
java.base/sun.nio.ch=ALL-UNNAMED
--add-opens
java.base/java.io=ALL-UNNAMED
--add-opens
java.rmi/sun.rmi.transport=ALL-UNNAMED
```

# Command Line (Non-GUI) Mode

Full usage guide: [usage guide](doc/usage.md) (Chinese). A quick parameter reference:

Besides the GUI, the tool can also run from the command line, which is convenient for packaging/CI flows. Use `--mode` to switch the launch mode; the default is GUI.

| Parameter | Description | Default |
|--------------------------|----------------------------------------------------------------------|-----------------|
| `--mode=gui\|none_gui` | Launch mode: `gui` for the GUI, `none_gui` for command line (also accepts `nogui`/`commandline`/`cli`) | `gui` |
| `--excel_path=./example` | Excel directory or a single Excel file; relative paths are supported (based on the working directory) | Excel path from system config |
| `--dest_path=./out` | Export directory; relative paths are supported (based on the working directory) | Export path from system config |
| `--lang=java` | Export languages, separated by commas; one of `java`/`json`/`go`/`rust`/`csharp`/`lua`/`cpp` | `java` |
| `--help` / `-h` | Print the usage guide and exit | - |

Examples:

```shell
# Export java by default; non-GUI mode appends json to ensure a full validation pass
ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out

# Export go and rust together
ConfigTool.exe --mode=none_gui --excel_path=./example --dest_path=./out --lang=go,rust
```

Notes:

1. Non-GUI mode always appends the json generator, so the full validation logic (field type / enum / duplicate sheet name checks) runs at least once.
2. In non-GUI mode, `excel_path`/`dest_path` passed on the command line are not written back to the system configuration file and do not overwrite the UI settings.
3. Results are shown on the console and in `log/ConfigTool.log`. Exit codes: 0 success, 1 generation failed, 2 no Excel file found.

# Adding Generated Templates to Your Project

## java

After the java templates are generated, add the following dependencies to the pom.xml of the project that references them:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<dependencies>
	<!--logging-->
	<dependency>
		<groupId>ch.qos.logback</groupId>
		<artifactId>logback-classic</artifactId>
		<version>1.2.11</version>
	</dependency>
	<!--excel parsing-->
	<dependency>
		<groupId>org.apache.poi</groupId>
		<artifactId>poi</artifactId>
		<version>4.1.2</version>
	</dependency>
	<!--excel parsing-->
	<dependency>
		<groupId>org.apache.poi</groupId>
		<artifactId>poi-ooxml</artifactId>
		<version>4.1.2</version>
	</dependency>
</dependencies>
```

# TODO

~~1. [Multi-language support](#multiLan) - further multi-language extension and selection.~~
~~2. Command line support~~
3. A display component for array configuration in the settings UI.