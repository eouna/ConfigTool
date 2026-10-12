using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.RegularExpressions;
using ClosedXML.Excel;

namespace ConfigTool.Cfg
{
    /// <summary>
    /// 配置数据源: 把配置文件打开成统一的工作薄视图, 后续解析由容器统一处理。
    /// </summary>
    public interface ConfigDataSource
    {
        /// <summary>字段元数据(字段名 + 配置表中的原始类型)</summary>
        public sealed class FieldMeta
        {
            public string Name { get; }
            public string RawType { get; }

            public FieldMeta(string name, string rawType)
            {
                Name = name;
                RawType = rawType;
            }
        }

        XLWorkbook OpenWorkbook(
            string rootPath,
            string fileName,
            string sheetName,
            List<FieldMeta> fields,
            int nameRow,
            int typeRow,
            int dataStartRow);

        /// <summary>文件摘要(用于热更新判断)</summary>
        string FileDigest(string rootPath, string fileName, string sheetName);

        /// <summary>是否"一张表只对应一个数据文件"(json为true, excel分表时为false)</summary>
        bool SingleSource { get; }

        /// <summary>创建数据源(json实现)</summary>
        public static ConfigDataSource Create()
        {
            return new JsonConfigDataSource();
        }
    }

    /// <summary>基于System.Text.Json + 内存工作薄的json数据源</summary>
    internal sealed class JsonConfigDataSource : ConfigDataSource
    {
        private static readonly Regex DateType =
            new Regex(@"^date<(.+)>$", RegexOptions.IgnoreCase | RegexOptions.Compiled);
        private static readonly Regex ListType =
            new Regex(@"^list<(.+)>\{(.)(\d*)\}$", RegexOptions.IgnoreCase | RegexOptions.Compiled);
        private static readonly Regex SetType =
            new Regex(@"^set<(.+)>\{(.)(\d*)\}$", RegexOptions.IgnoreCase | RegexOptions.Compiled);
        private static readonly Regex MapType =
            new Regex(@"^map<(.+)>\{(.)(\d*)\}$", RegexOptions.IgnoreCase | RegexOptions.Compiled);
        private static readonly Regex MapInner =
            new Regex(@"^(.+?)\{(.)\}(.+)$", RegexOptions.Compiled);

        public XLWorkbook OpenWorkbook(
            string rootPath,
            string fileName,
            string sheetName,
            List<ConfigDataSource.FieldMeta> fields,
            int nameRow,
            int typeRow,
            int dataStartRow)
        {
            using JsonDocument document = ReadJson(rootPath, sheetName);
            var workbook = new XLWorkbook();
            var sheet = workbook.Worksheets.Add(sheetName);
            int colCount = fields?.Count ?? 0;
            int headRowCount = Math.Max(dataStartRow, Math.Max(nameRow, typeRow) + 1);
            for (int rowIndex = 0; rowIndex < headRowCount; rowIndex++)
            {
                for (int colIndex = 0; colIndex < colCount; colIndex++)
                {
                    ConfigDataSource.FieldMeta meta = fields[colIndex];
                    string headValue = rowIndex == typeRow
                        ? (meta.RawType ?? string.Empty)
                        : meta.Name;
                    sheet.Cell(rowIndex + 1, colIndex + 1).Value = headValue;
                }
            }
            if (document.RootElement.ValueKind != JsonValueKind.Object || fields == null)
            {
                return workbook;
            }

            List<JsonProperty> items = document.RootElement
                .EnumerateObject()
                .Where(item => item.Value.ValueKind == JsonValueKind.Object)
                .Where(item => !string.Equals(item.Name.Trim(), "0", StringComparison.Ordinal))
                .OrderBy(item => item, Comparer<JsonProperty>.Create((left, right) => CompareId(left.Name, right.Name)))
                .ToList();
            int dataRow = dataStartRow;
            foreach (JsonProperty item in items)
            {
                bool hasValue = false;
                for (int colIndex = 0; colIndex < colCount; colIndex++)
                {
                    ConfigDataSource.FieldMeta meta = fields[colIndex];
                    if (!item.Value.TryGetProperty(meta.Name, out JsonElement value)
                        || value.ValueKind == JsonValueKind.Null
                        || value.ValueKind == JsonValueKind.Undefined)
                    {
                        continue;
                    }
                    sheet.Cell(dataRow + 1, colIndex + 1).Value =
                        ValueToConfigString(meta.RawType, value);
                    hasValue = true;
                }
                if (hasValue)
                {
                    dataRow++;
                }
            }
            return workbook;
        }

        public string FileDigest(string rootPath, string fileName, string sheetName)
        {
            string fullPath = Path.Combine(rootPath, sheetName + ".json");
            using var stream = File.OpenRead(fullPath);
            byte[] hash = MD5.HashData(stream);
            return Convert.ToHexString(hash).ToLowerInvariant();
        }

        public bool SingleSource => true;

        private static JsonDocument ReadJson(string rootPath, string sheetName)
        {
            string fullPath = Path.Combine(rootPath, sheetName + ".json");
            if (!File.Exists(fullPath))
            {
                throw new FileNotFoundException("找不到json数据文件: " + fullPath, fullPath);
            }
            using var stream = File.OpenRead(fullPath);
            return JsonDocument.Parse(stream);
        }

        private static string ValueToConfigString(string rawType, JsonElement value)
        {
            if (value.ValueKind == JsonValueKind.Null || value.ValueKind == JsonValueKind.Undefined)
            {
                return string.Empty;
            }
            string raw = (rawType ?? string.Empty).Trim();
            Match dateMatch = DateType.Match(raw);
            if (dateMatch.Success)
            {
                return FormatDate(dateMatch.Groups[1].Value, value.GetString());
            }
            Match listMatch = ListType.Match(raw);
            if (listMatch.Success)
            {
                return JoinValues(listMatch.Groups[1].Value, listMatch.Groups[2].Value, value);
            }
            Match setMatch = SetType.Match(raw);
            if (setMatch.Success)
            {
                return JoinValues(setMatch.Groups[1].Value, setMatch.Groups[2].Value, value);
            }
            Match mapMatch = MapType.Match(raw);
            if (mapMatch.Success)
            {
                return JoinMapValues(mapMatch.Groups[1].Value, mapMatch.Groups[2].Value, value);
            }
            return ScalarToString(value);
        }

        private static string JoinValues(string subType, string delimiter, JsonElement value)
        {
            if (value.ValueKind != JsonValueKind.Array)
            {
                return ScalarToString(value);
            }
            var parts = new List<string>();
            foreach (JsonElement item in value.EnumerateArray())
            {
                parts.Add(ValueToConfigString(subType, item));
            }
            return string.Join(delimiter, parts);
        }

        private static string JoinMapValues(string innerType, string entryDelimiter, JsonElement value)
        {
            Match inner = MapInner.Match(innerType);
            if (!inner.Success || value.ValueKind != JsonValueKind.Object)
            {
                return ScalarToString(value);
            }
            string keyValueDelimiter = inner.Groups[2].Value;
            string valueType = inner.Groups[3].Value;
            List<JsonProperty> entries = value
                .EnumerateObject()
                .OrderBy(item => item, Comparer<JsonProperty>.Create((left, right) => CompareId(left.Name, right.Name)))
                .ToList();
            var parts = new List<string>();
            foreach (JsonProperty entry in entries)
            {
                parts.Add(
                    entry.Name
                    + keyValueDelimiter
                    + ValueToConfigString(valueType, entry.Value));
            }
            return string.Join(entryDelimiter, parts);
        }

        private static string ScalarToString(JsonElement value)
        {
            switch (value.ValueKind)
            {
                case JsonValueKind.String:
                    return value.GetString() ?? string.Empty;
                case JsonValueKind.True:
                    return "true";
                case JsonValueKind.False:
                    return "false";
                case JsonValueKind.Number:
                    return value.GetRawText();
                default:
                    return value.GetRawText();
            }
        }

        private static string FormatDate(string format, string text)
        {
            if (string.IsNullOrWhiteSpace(text))
            {
                return text ?? string.Empty;
            }
            if (DateTimeOffset.TryParse(
                    text,
                    CultureInfo.InvariantCulture,
                    DateTimeStyles.AllowWhiteSpaces,
                    out DateTimeOffset parsed))
            {
                return parsed.ToString(format, CultureInfo.InvariantCulture);
            }
            return text;
        }

        private static int CompareId(string left, string right)
        {
            if (long.TryParse(left, NumberStyles.Integer, CultureInfo.InvariantCulture, out long leftNumber)
                && long.TryParse(right, NumberStyles.Integer, CultureInfo.InvariantCulture, out long rightNumber))
            {
                return leftNumber.CompareTo(rightNumber);
            }
            return string.Compare(left, right, StringComparison.Ordinal);
        }
    }
}
