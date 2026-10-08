using System;
using System.Collections.Generic;

namespace ConfigTool.Cfg
{
    /// <summary>
    /// @excelName ${dataStruct.fileName}
    /// @sheetName ${dataStruct.sheetName}
    /// @date ${date}
    /// </summary>
    public class ${beanClassName} : ICfgBean
    {
        /// <summary>${idName}</summary>
        public int ${idName} { get; set; }
<#list dataStruct.excelFieldInfoList as excelFieldInfo>

        /// <summary>${excelFieldInfo.fieldDesc.fieldData}</summary>
        public ${excelFieldInfo.fieldType.fieldData} ${excelFieldInfo.fieldName.fieldData} { get; set; }<#if excelFieldInfo.fieldType.fieldData?contains("List<") || excelFieldInfo.fieldType.fieldData?contains("Dictionary<")> = new();</#if>
</#list>

        /// <summary>配置ID</summary>
        public int GetId()
        {
            return ${idName};
        }

        public override string ToString()
        {
            return "${beanClassName}{" + "${idName}=" + CfgFormat.Value(${idName})<#list dataStruct.excelFieldInfoList as excelFieldInfo> + ", ${excelFieldInfo.fieldName.fieldData}=" + CfgFormat.Value(${excelFieldInfo.fieldName.fieldData})</#list> + "}";
        }
<#list enumDefs as enumDef>

        /// <summary>${enumDef.name} 枚举(该配置表字段使用的枚举)</summary>
        public enum ${enumDef.name}
        {
<#list enumDef.values as value>
            ${value?cap_first},
</#list>
        }

        /// <summary>根据字符串获取${enumDef.name}</summary>
        public static ${enumDef.name} Parse${enumDef.name}(string value)
        {
            return (${enumDef.name})Enum.Parse(typeof(${enumDef.name}), value, true);
        }
</#list>
    }
}