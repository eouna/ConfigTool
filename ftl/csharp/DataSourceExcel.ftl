using System;
using System.IO;
using System.Security.Cryptography;
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
            System.Collections.Generic.List<FieldMeta> fields,
            int nameRow,
            int typeRow,
            int dataStartRow);

        /// <summary>文件摘要(用于热更新判断)</summary>
        string FileDigest(string rootPath, string fileName, string sheetName);

        /// <summary>是否"一张表只对应一个数据文件"(json为true, excel分表时为false)</summary>
        bool SingleSource { get; }

        /// <summary>创建数据源(excel实现)</summary>
        public static ConfigDataSource Create()
        {
            return new ExcelConfigDataSource();
        }
    }

    /// <summary>基于ClosedXML的excel数据源</summary>
    internal sealed class ExcelConfigDataSource : ConfigDataSource
    {
        public XLWorkbook OpenWorkbook(
            string rootPath,
            string fileName,
            string sheetName,
            System.Collections.Generic.List<ConfigDataSource.FieldMeta> fields,
            int nameRow,
            int typeRow,
            int dataStartRow)
        {
            return new XLWorkbook(Path.Combine(rootPath, fileName));
        }

        public string FileDigest(string rootPath, string fileName, string sheetName)
        {
            string fullPath = Path.Combine(rootPath, fileName);
            using var stream = File.OpenRead(fullPath);
            byte[] hash = MD5.HashData(stream);
            return Convert.ToHexString(hash).ToLowerInvariant();
        }

        public bool SingleSource => false;
    }
}
