[English](configuration_EN.md) | [简体中文](configuration.md) | [Back to README](../README_EN.md)

# Config Table Tool - Configuration Guide

This document describes the **table layout rules, field types, validation rules, split-table rules** and **how to use the generated output in your project**.

- For an overview, supported languages and screenshots, see the [README](../README_EN.md)
- For command line (non-GUI) parameters, see the [usage guide](usage.md) (Chinese)

## Contents

1. [Configuration Notes](#1-configuration-notes)
2. [Field Naming](#2-field-naming)
3. [Field Type Configuration](#3-field-type-configuration)
4. [Validation Rules](#4-validation-rules)
5. [Split Tables](#5-split-tables)
6. [Example Configuration](#6-example-configuration)
7. [Excel Directory Mapping](#7-excel-directory-mapping)
8. [Running from Source](#8-running-from-source)
9. [Using Generated Templates in Your Project](#9-using-generated-templates-in-your-project)
10. [Feature List](#10-feature-list)

---

## 1. Configuration Notes

1. Because the meaning of each Excel row differs between projects, the basic rows cannot be omitted. This tool requires four rows to describe a table: the **field description row, the field type row, the field name row and the field data range row**.
2. The tool only reads the data of the first sheet in the Excel file, and the generated configuration bean is named after the sheet name plus `Cfg`. For example, if the sheet name is `Activity`, the generated configuration class is `ActivityCfg`; if the sheet name is already `ActivityCfg`, that name is used directly.
3. The first column of every configuration table must be the ID column, named `id`. The ID column name is configurable and defaults to `id`.

## 2. Field Naming

1. A field name cannot start with a digit or other special characters, except `_`. Names may start with `_`, but this is not recommended.
2. Whether `_`-joined field names are converted to camelCase is configurable.

## 3. Field Type Configuration

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

## 4. Validation Rules

1. The tool checks whether the data matches the configured field type.
2. For compatibility with int: if the value read for an int field is a floating-point number, it is rounded down.
3. The first column of every configuration table must be the ID column. Its format is not checked and its type may be omitted, defaulting to int.
4. The description row can be customized, but the last field-description row must be adjacent to the data row. See the example Excel for details.

> A pre-check is performed before generation (configurable in the UI) to catch data problems in the tables early.

## 5. Split Tables

Split tables are distinguished by the Excel file name: if the name contains `_`, it is a split table. Currently only two levels are supported; multi-level split tables may be supported later. Tables are separated by `_`, and files whose names before `_` are identical are treated as one group of split tables.

**Note: field names must not be duplicated across the tables in one split group!**

### When to Use

1. A single table becomes too large.
2. The table data can be distinguished by some type.

### Generated Code Structure

If there are two sub-tables, three tables are generated: two sub-tables and one parent table. The parent table contains the data of all sub-tables, and each sub-table also contains its own data.

## 6. Example Configuration

1. For reference, the project contains a basic Excel file that lists some basic usages. [reference document](../example/example.xlsx)

## 7. Excel Directory Mapping

The tool can map the configuration directory used by designers to the project directory used by the program. The configuration file is `ExcelDirBindServerModuleConfLocal.txt`.

## 8. Running from Source

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

> These are not needed when using the packaged green / single-file build; the launch script already includes them.

## 9. Using Generated Templates in Your Project

### java

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
---

## 10. Feature List

1. Generates code that loads Excel data in multiple languages, manages table data through a data manager, and provides a friendly way to read configuration data
2. Validates the configuration table format and fields during generation, so problems are found early
3. Supports uploading configuration tables: generate -> validate -> upload
4. Uses multithreading to speed up template generation; data loading can also be accelerated
5. Rich settings for different scenarios: reading by directory structure, keeping the Excel directory, and various optional configuration items
6. Supports split tables (sub-tables)
7. Supports pre-checking, used for validating the data in configuration tables
8. Supports exporting configuration data per endpoint (Client / Server); export restrictions can be controlled per field
9. Supports constant configuration in configuration tables
