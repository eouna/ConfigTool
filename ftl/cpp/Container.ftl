#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "CfgDefs.h"
#include "DataSource.h"
#include "bean/${beanClassName}.h"
#include "container/BaseCfgContainer.h"

/// ${containerClassName} ${excelName}配置容器(自动生成)
/// @sheetName ${sheetName}
/// @date ${date}
class ${containerClassName} : public cfg::BaseCfgContainer<${beanClassName}> {
 public:
    /// 当前容器对应的表名(json数据源按 <表名>.json 读取)
    static const char* SheetName() { return "${sheetName}"; }

    /// 字段元数据(字段名 + 配置表中的原始类型)
    static const std::vector<cfg::FieldMeta>& FieldMetas() {
        static const std::vector<cfg::FieldMeta> kFieldMetas = {
<#list fieldMetas as meta>
            cfg::FieldMeta{"${meta.name}", "${meta.rawType}"},
</#list>
        };
        return kFieldMetas;
    }

    /// 绑定的excel文件列表
    static const std::vector<std::string>& ExcelNames() {
        static const std::vector<std::string> kExcelNames = {
            "${excelName}",
        };
        return kExcelNames;
    }

    /// 运行期读取数据源并解析(与Java模板的loadData等价)
    void Load(const std::string& resourceRootPath) override {
        cfg::DataSource dataSource;
        std::vector<std::string> sourceNames;
        if (dataSource.SingleSource()) {
            sourceNames.push_back(std::string(SheetName()) + ".xlsx");
        } else {
            sourceNames = ExcelNames();
        }

        CfgMap temp;
        for (const std::string& sourceName : sourceNames) {
            cfg::SheetReadRequest request;
            request.rootPath = resourceRootPath;
            request.fileName = sourceName;
            request.sheetName = SheetName();
            request.fields = FieldMetas();
            request.nameRow = m_fieldNameRow;
            request.typeRow = m_fieldTypeRow;
            request.dataStartRow = m_dataStartRow;
            request.constantSheetName = m_constantSheetName;

            const cfg::Rows rows = dataSource.ReadSheet(request);
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
            cfg::Rows constRows;
            if (dataSource.ReadConstantRows(request, constRows)) {
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
        }
        m_cfgMap = std::move(temp);
    }
<#list constantFields as constField>

    /// ${constField.desc}
    ${constField.type} ${constField.name}{};
</#list>
};