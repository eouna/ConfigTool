using System;
using System.Collections.Generic;
using ClosedXML.Excel;

namespace ConfigTool.Cfg
{
    /// <summary>
    /// @excelName ${excelName}
    /// @sheetName ${sheetBean.sheetName}
    /// @date ${date}
    /// </summary>
    public class ${containerClassName} : BaseCfgContainer<${beanClassName}>
    {
<#list constantFields as constField>
        /// <summary>${constField.fieldDesc.fieldData}</summary>
        public ${constField.fieldType.fieldData} ${constField.fieldName.fieldData} { get; set; }<#if constField.fieldType.fieldData?contains("List<") || constField.fieldType.fieldData?contains("Dictionary<")> = new();</#if>

</#list>
        protected override ${beanClassName} CreateNewBean()
        {
            return new ${beanClassName}();
        }

        public override List<string> GetExcelNameList()
        {
            return new List<string>
            {
<#list bindExcelList as excelName>
                "${excelName}",
</#list>
            };
        }
<#if (constantFields?size > 0)>

        protected override void LoadConstants(XLWorkbook workbook)
        {
            if (!workbook.Worksheets.TryGetWorksheet(ConstantSheetName, out var sheet))
            {
                return;
            }
            int lastRow = sheet.LastRowUsed()?.RowNumber() ?? 0;
            for (int row = 2; row <= lastRow; row++)
            {
                string name = CellText(sheet, row, ConstFieldNameRow + 1);
                if (string.IsNullOrEmpty(name))
                {
                    continue;
                }
                string rawType = CellText(sheet, row, ConstFieldTypeRow + 1);
                string valueText = CellText(sheet, row, ConstFieldDataVal + 1);
                switch (name)
                {
<#list constantFields as constField>
                    case "${constField.fieldName.fieldData}":
                        ${constField.fieldName.fieldData} = (${constField.fieldType.fieldData})ParseValue(rawType, valueText, typeof(${constField.fieldType.fieldData}));
                        break;
</#list>
                    default:
                        break;
                }
            }
        }
</#if>
    }
}