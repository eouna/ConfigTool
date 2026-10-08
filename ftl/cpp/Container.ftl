#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "CfgDefs.h"
#include "XlsxReader.h"
#include "bean/${beanClassName}.h"
#include "container/BaseCfgContainer.h"

/// ${containerClassName} ${excelName}配置容器(自动生成)
/// @sheetName ${sheetName}
/// @date ${date}
class ${containerClassName} : public cfg::BaseCfgContainer<${beanClassName}> {
 public:
    /// 运行期读取excel并解析(与Java模板的loadData等价)
    void Load(const std::string& resourceRootPath) override {
        cfg::XlsxWorkbook workbook(cfg::JoinPath(resourceRootPath, "${excelName}"));
        cfg::Rows rows = workbook.SheetAt(0);
        const cfg::Row& names = cfg::RowAt(rows, m_fieldNameRow);
        const cfg::Row& types = cfg::RowAt(rows, m_fieldTypeRow);
        const cfg::Row& dataRangeRow = cfg::RowAt(rows, m_fieldDataRangeRow);

        // 数据范围列标记为跳过值的列不加载
        std::vector<bool> skipColumns(names.size(), false);
        if (!m_skipStr.empty()) {
            for (std::size_t i = 0; i < names.size() && i < dataRangeRow.size(); ++i) {
                if (cfg::Trim(dataRangeRow[i]) == m_skipStr) {
                    skipColumns[i] = true;
                }
            }
        }

        CfgMap temp;
        for (std::size_t rowIndex = static_cast<std::size_t>(m_dataStartRow); rowIndex < rows.size(); ++rowIndex) {
            const cfg::Row& row = rows[rowIndex];
            if (row.empty()) {
                continue;
            }
            ${beanClassName} bean{};
            std::size_t column = 0;
            if (cfg::FindColumn(names, "${idName}", column)
                && (column >= skipColumns.size() || !skipColumns[column])) {
                bean.${idName} = cfg::CfgValue<int32_t>::Parse(cfg::TypeAt(types, column), cfg::CellAt(row, column));
            }
<#list fields as field>
            if (cfg::FindColumn(names, "${field.name}", column)
                && (column >= skipColumns.size() || !skipColumns[column])) {
                bean.${field.name} = cfg::CfgValue<${field.qualifiedType}>::Parse(cfg::TypeAt(types, column), cfg::CellAt(row, column));
            }
</#list>
            if (temp.find(bean.${idName}) != temp.end()) {
                throw std::runtime_error("出现重复的ID: " + std::to_string(bean.${idName}));
            }
            temp.emplace(bean.${idName}, bean);
        }
<#if (constantFields?size > 0)>
        if (workbook.HasSheet(m_constantSheetName)) {
            cfg::Rows constRows = workbook.Sheet(m_constantSheetName);
            for (std::size_t rowIndex = 1; rowIndex < constRows.size(); ++rowIndex) {
                const cfg::Row& row = constRows[rowIndex];
                std::string name = cfg::CellAt(row, static_cast<std::size_t>(m_constFieldNameRow));
                if (name.empty()) {
                    continue;
                }
                std::string rawType = cfg::CellAt(row, static_cast<std::size_t>(m_constFieldTypeRow));
                std::string text = cfg::CellAt(row, static_cast<std::size_t>(m_constFieldDataVal));
<#list constantFields as constField>
                if (name == "${constField.name}") {
                    ${constField.name} = cfg::CfgValue<${constField.qualifiedType}>::Parse(rawType, text);
                    continue;
                }
</#list>
            }
        }
</#if>
        m_cfgMap = std::move(temp);
    }
<#list constantFields as constField>

    /// ${constField.desc}
    ${constField.type} ${constField.name}{};
</#list>
};