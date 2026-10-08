using System;
using System.Collections;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Reflection;
using ClosedXML.Excel;

namespace ConfigTool.Cfg
{
    /// <summary>配置Bean接口</summary>
    public interface ICfgBean
    {
        int GetId();
    }

    /// <summary>调试输出格式化工具</summary>
    public static class CfgFormat
    {
        public static string Value(object value)
        {
            if (value == null)
            {
                return "null";
            }
            if (value is IDictionary dictionary)
            {
                var parts = new List<string>();
                foreach (DictionaryEntry entry in dictionary)
                {
                    parts.Add(Value(entry.Key) + ":" + Value(entry.Value));
                }
                return "{" + string.Join(", ", parts) + "}";
            }
            if (value is IEnumerable enumerable && !(value is string))
            {
                var parts = new List<string>();
                foreach (var item in enumerable)
                {
                    parts.Add(Value(item));
                }
                return "[" + string.Join(", ", parts) + "]";
            }
            if (value is DateTime time)
            {
                return time.ToString("yyyy-MM-dd HH:mm:ss");
            }
            return value.ToString();
        }
    }

    /// <summary>配置表容器基类(反射驱动解析)</summary>
    /// <summary>配置容器接口</summary>
    public interface ICfgContainer
    {
        /// <summary>容器名</summary>
        string ContainerName { get; }

        /// <summary>加载配置表数据</summary>
        void LoadData(string resourceRootPath);
    }

    /// <summary>配置表容器基类(反射驱动解析)</summary>
    public abstract class BaseCfgContainer<T> : ICfgContainer where T : ICfgBean, new()
    {
        /// <summary>字段描述行</summary>
        protected int FieldDescRow = ${fieldInfo.fieldDesc.configBindRow};
        /// <summary>字段类型行</summary>
        protected int FieldTypeRow = ${fieldInfo.fieldType.configBindRow};
        /// <summary>字段名行</summary>
        protected int FieldNameRow = ${fieldInfo.fieldName.configBindRow};
        /// <summary>字段数值范围行</summary>
        protected int FieldDataRangeRow = ${fieldInfo.fieldDataRange.configBindRow};
        /// <summary>数据读取开始行数</summary>
        protected int DataStartRow = ${dataStartRow};

        /// <summary>常量字段名列</summary>
        protected int ConstFieldNameRow = ${constFieldInfo.fieldName.configBindRow};
        /// <summary>常量字段类型列</summary>
        protected int ConstFieldTypeRow = ${constFieldInfo.fieldType.configBindRow};
        /// <summary>常量字段值列</summary>
        protected int ConstFieldDataVal = ${constFieldInfo.fieldVal.configBindRow};
        /// <summary>常量字段描述列</summary>
        protected int ConstFieldDescRow = ${constFieldInfo.fieldDesc.configBindRow};
        /// <summary>常量工作薄名</summary>
        protected string ConstantSheetName = "${constantSheetName}";
        /// <summary>需要跳过的列标记</summary>
        protected string SkipStr = "${skipStr}";

        /// <summary>容器名</summary>
        public virtual string ContainerName => GetType().Name;

        /// <summary>配置ID对应的配置数据</summary>
        public Dictionary<int, T> CfgBeanMap { get; protected set; } = new Dictionary<int, T>();

        /// <summary>绑定的excel文件列表</summary>
        public abstract List<string> GetExcelNameList();

        /// <summary>创建新的bean</summary>
        protected abstract T CreateNewBean();

        /// <summary>加载配置表数据</summary>
        public virtual void LoadData(string resourceRootPath)
        {
            if (string.IsNullOrWhiteSpace(resourceRootPath))
            {
                throw new ArgumentException("bind excel path list is empty");
            }
            var tempMap = new Dictionary<int, T>();
            foreach (var excelName in GetExcelNameList())
            {
                var fullPath = Path.Combine(resourceRootPath, excelName);
                using var workbook = new XLWorkbook(fullPath);
                var sheet = workbook.Worksheet(1);
                int lastRow = sheet.LastRowUsed()?.RowNumber() ?? 0;
                int lastCol = sheet.LastColumnUsed()?.ColumnNumber() ?? 0;

                var names = new List<string>();
                var types = new List<string>();
                for (int col = 1; col <= lastCol; col++)
                {
                    names.Add(CellText(sheet, FieldNameRow + 1, col));
                    types.Add(CellText(sheet, FieldTypeRow + 1, col));
                }
                var skipCols = GetSkipColumns(sheet, lastCol);
                for (int row = DataStartRow + 1; row <= lastRow; row++)
                {
                    if (IsBlankRow(sheet, row, lastCol))
                    {
                        continue;
                    }
                    var bean = CreateNewBean();
                    for (int col = 1; col <= lastCol; col++)
                    {
                        if (skipCols.Contains(col - 1))
                        {
                            continue;
                        }
                        string fieldName = names[col - 1];
                        if (string.IsNullOrEmpty(fieldName))
                        {
                            continue;
                        }
                        var member = FindMember(typeof(T), fieldName);
                        if (member == null)
                        {
                            continue;
                        }
                        var value = ParseValue(types[col - 1], CellText(sheet, row, col), GetMemberType(member));
                        SetMember(bean, member, value);
                    }
                    int id = bean.GetId();
                    if (tempMap.ContainsKey(id))
                    {
                        throw new InvalidOperationException($"出现重复的ID: {id}");
                    }
                    tempMap[id] = bean;
                }
                LoadConstants(workbook);
            }
            CfgBeanMap = tempMap;
        }

        /// <summary>加载常量字段(由子类覆写)</summary>
        protected virtual void LoadConstants(XLWorkbook workbook)
        {
        }

        protected static string CellText(IXLWorksheet sheet, int row, int col)
        {
            var cell = sheet.Cell(row, col);
            if (cell == null || cell.IsEmpty())
            {
                return string.Empty;
            }
            return (cell.GetString() ?? string.Empty).Trim();
        }

        private HashSet<int> GetSkipColumns(IXLWorksheet sheet, int lastCol)
        {
            var skip = new HashSet<int>();
            if (string.IsNullOrEmpty(SkipStr))
            {
                return skip;
            }
            for (int col = 1; col <= lastCol; col++)
            {
                if (string.Equals(CellText(sheet, FieldDataRangeRow + 1, col), SkipStr, StringComparison.OrdinalIgnoreCase))
                {
                    skip.Add(col - 1);
                }
            }
            return skip;
        }

        private static bool IsBlankRow(IXLWorksheet sheet, int row, int lastCol)
        {
            for (int col = 1; col <= lastCol; col++)
            {
                if (!string.IsNullOrEmpty(CellText(sheet, row, col)))
                {
                    return false;
                }
            }
            return true;
        }

        private static MemberInfo FindMember(Type type, string name)
        {
            foreach (var property in type.GetProperties(BindingFlags.Public | BindingFlags.Instance))
            {
                if (string.Equals(property.Name, name, StringComparison.OrdinalIgnoreCase))
                {
                    return property;
                }
            }
            foreach (var field in type.GetFields(BindingFlags.Public | BindingFlags.Instance))
            {
                if (string.Equals(field.Name, name, StringComparison.OrdinalIgnoreCase))
                {
                    return field;
                }
            }
            return null;
        }

        private static Type GetMemberType(MemberInfo member)
        {
            if (member is PropertyInfo property)
            {
                return property.PropertyType;
            }
            if (member is FieldInfo field)
            {
                return field.FieldType;
            }
            return typeof(object);
        }

        private static void SetMember(object bean, MemberInfo member, object value)
        {
            if (member is PropertyInfo property)
            {
                property.SetValue(bean, value);
            }
            else if (member is FieldInfo field)
            {
                field.SetValue(bean, value);
            }
        }

        /// <summary>按excel类型描述解析字段值</summary>
        protected static object ParseValue(string rawType, string text, Type targetType)
        {
            var type = (rawType ?? string.Empty).Trim();
            if (string.IsNullOrEmpty(text))
            {
                return targetType.IsValueType ? Activator.CreateInstance(targetType) : null;
            }
            if (type.StartsWith("date<", StringComparison.OrdinalIgnoreCase))
            {
                var format = type.Substring(type.IndexOf('<') + 1).TrimEnd('>');
                return DateTime.ParseExact(NormalizeWhitespace(text), format, CultureInfo.InvariantCulture);
            }
            if (type.StartsWith("list<", StringComparison.OrdinalIgnoreCase)
                || type.StartsWith("set<", StringComparison.OrdinalIgnoreCase))
            {
                var spec = ParseSeqSpec(type);
                var elementType = targetType.IsGenericType ? targetType.GetGenericArguments()[0] : typeof(object);
                var list = (IList)Activator.CreateInstance(typeof(List<>).MakeGenericType(elementType));
                var parts = text.Split(spec.Delimiter);
                if (spec.Limit > 0 && parts.Length > spec.Limit)
                {
                    throw new InvalidOperationException($"字段对应的数据数量: {parts.Length} 超过限制值: {spec.Limit}");
                }
                bool isSet = type.StartsWith("set<", StringComparison.OrdinalIgnoreCase);
                var seen = new HashSet<string>();
                foreach (var part in parts)
                {
                    var item = part.Trim();
                    if (item.Length == 0)
                    {
                        continue;
                    }
                    if (isSet && !seen.Add(item))
                    {
                        throw new InvalidOperationException($"Set列数据出现重复数据: {item}");
                    }
                    list.Add(ParseValue(spec.SubType, item, elementType));
                }
                return list;
            }
            if (type.StartsWith("map<", StringComparison.OrdinalIgnoreCase))
            {
                var spec = ParseMapSpec(type);
                var dictionary = (IDictionary)Activator.CreateInstance(targetType);
                var keyType = targetType.GetGenericArguments()[0];
                var valueType = targetType.GetGenericArguments()[1];
                var entries = text.Split(spec.EntryDelimiter);
                if (spec.Limit > 0 && entries.Length > spec.Limit)
                {
                    throw new InvalidOperationException($"字段对应的数据数量: {entries.Length} 超过限制值: {spec.Limit}");
                }
                foreach (var entry in entries)
                {
                    var item = entry.Trim();
                    if (item.Length == 0)
                    {
                        continue;
                    }
                    var pair = item.Split(new[] { spec.KeyDelimiter }, 2);
                    if (pair.Length < 2)
                    {
                        throw new InvalidOperationException($"map数据格式错误: {item}");
                    }
                    var key = ParseValue(spec.KeyType, pair[0].Trim(), keyType);
                    var value = ParseValue(spec.ValueType, pair[1].Trim(), valueType);
                    dictionary[key] = value;
                }
                return dictionary;
            }
            var trimmed = text.Trim();
            if (targetType == typeof(string))
            {
                return text;
            }
            if (targetType == typeof(bool))
            {
                return bool.Parse(trimmed.ToLowerInvariant());
            }
            if (targetType.IsEnum)
            {
                return Enum.Parse(targetType, trimmed, true);
            }
            var numberText = IntPart(trimmed);
            if (targetType == typeof(sbyte))
            {
                return sbyte.Parse(numberText, CultureInfo.InvariantCulture);
            }
            if (targetType == typeof(short))
            {
                return short.Parse(numberText, CultureInfo.InvariantCulture);
            }
            if (targetType == typeof(int))
            {
                return int.Parse(numberText, CultureInfo.InvariantCulture);
            }
            if (targetType == typeof(long))
            {
                return long.Parse(numberText, CultureInfo.InvariantCulture);
            }
            if (targetType == typeof(float))
            {
                return float.Parse(trimmed, CultureInfo.InvariantCulture);
            }
            if (targetType == typeof(double))
            {
                return double.Parse(trimmed, CultureInfo.InvariantCulture);
            }
            return Convert.ChangeType(trimmed, targetType, CultureInfo.InvariantCulture);
        }

        /// <summary>把连续空白折叠为单个空格(兼容excel中多空格的时间)</summary>
        private static string NormalizeWhitespace(string value)
        {
            var builder = new System.Text.StringBuilder(value.Length);
            bool lastWasSpace = false;
            foreach (var ch in value.Trim())
            {
                if (char.IsWhiteSpace(ch))
                {
                    if (!lastWasSpace)
                    {
                        builder.Append(' ');
                        lastWasSpace = true;
                    }
                }
                else
                {
                    builder.Append(ch);
                    lastWasSpace = false;
                }
            }
            return builder.ToString();
        }

        private static string IntPart(string value)
        {
            var index = value.IndexOf('.');
            return index > 0 ? value.Substring(0, index) : value;
        }

        private static (string SubType, char Delimiter, int Limit) ParseSeqSpec(string rawType)
        {
            int start = rawType.IndexOf('<');
            int brace = rawType.LastIndexOf('{');
            var subType = rawType.Substring(start + 1, brace - 1 - (start + 1)).Trim();
            var inner = rawType.Substring(brace + 1);
            char delimiter = inner.Length > 0 ? inner[0] : ',';
            var digits = new string(inner.Where(char.IsDigit).ToArray());
            int limit = digits.Length > 0 ? int.Parse(digits) : 0;
            return (subType, delimiter, limit);
        }

        private static (string KeyType, char KeyDelimiter, string ValueType, char EntryDelimiter, int Limit)
            ParseMapSpec(string rawType)
        {
            int start = rawType.IndexOf('<');
            int brace = rawType.LastIndexOf('{');
            var inner = rawType.Substring(brace + 1);
            char entryDelimiter = inner.Length > 0 ? inner[0] : ';';
            var digits = new string(inner.Where(char.IsDigit).ToArray());
            int limit = digits.Length > 0 ? int.Parse(digits) : 0;
            var body = rawType.Substring(start + 1, brace - 1 - (start + 1));
            int keyBrace = body.IndexOf('{');
            int keyClose = body.IndexOf('}', keyBrace);
            var keyType = body.Substring(0, keyBrace).Trim();
            char keyDelimiter = body.Substring(keyBrace + 1, keyClose - keyBrace - 1)[0];
            var valueType = body.Substring(keyClose + 1).Trim();
            return (keyType, keyDelimiter, valueType, entryDelimiter, limit);
        }
    }
}