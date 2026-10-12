#pragma once

#include <string>
#include <vector>

#include "CfgDefs.h"
#include "XlsxReader.h"

namespace cfg {

/// 字段元数据(字段名 + 配置表中的原始类型)
struct FieldMeta {
    std::string name;
    std::string rawType;
};

/// 读取一张表的请求参数
struct SheetReadRequest {
    std::string rootPath;
    std::string fileName;
    std::string sheetName;
    std::vector<FieldMeta> fields;
    int nameRow = 0;
    int typeRow = 0;
    int dataStartRow = 0;
    std::string constantSheetName;
};

/// 配置数据源(excel实现)
class DataSource {
 public:
    /// 读取工作薄首个sheet(行 x 列文本)
    Rows ReadSheet(const SheetReadRequest& request) const {
        XlsxWorkbook workbook(JoinPath(request.rootPath, request.fileName));
        return workbook.SheetAt(0);
    }

    /// 读取常量表(不存在时返回false)
    bool ReadConstantRows(const SheetReadRequest& request, Rows& rows) const {
        if (request.constantSheetName.empty()) {
            return false;
        }
        XlsxWorkbook workbook(JoinPath(request.rootPath, request.fileName));
        if (!workbook.HasSheet(request.constantSheetName)) {
            return false;
        }
        rows = workbook.Sheet(request.constantSheetName);
        return true;
    }

    /// excel分表时一个容器可能对应多个文件
    bool SingleSource() const { return false; }
};

}  // namespace cfg
