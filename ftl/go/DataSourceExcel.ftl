package ${packageName}

// 本文件由配置表工具生成: 数据源读取实现(excel)
//
// 约定: 每种语言把"数据源读取"独立成一个类, 并通过两个模板提供两种实现
//   DataSourceExcel.ftl -> 读取excel
//   DataSourceJson.ftl  -> 读取json
// 界面「数据加载选项」选择哪个, 生成时就使用哪个模板。

import (
	"crypto/md5"
	"encoding/hex"
	"os"
	"path/filepath"

	"github.com/xuri/excelize/v2"
)

// FieldMeta 字段元数据(字段名 + 配置表中的原始类型), 按列顺序排列
type FieldMeta struct {
	Name    string
	RawType string
}

// SheetReadRequest 读取一张表的请求参数
type SheetReadRequest struct {
	// 数据根目录
	RootPath string
	// excel文件名(json数据源不使用)
	FileName string
	// 表名(工作薄名); json数据源按 <表名>.json 读取
	SheetName string
	// 常量表名: excel数据源据此读取常量工作薄; json数据源的常量在 <表名>.json 的const段
	ConstSheetName string
	// 字段元数据(按列顺序), json数据源据此还原表头
	Fields []FieldMeta
	// 表头行索引(与容器配置一致)
	NameRow int
	TypeRow int
	RangeRow int
	// 数据起始行
	DataStartRow int
}

// DataSource 数据源接口: 把配置表读成"字符串表格"(与excel单元格的表示一致),
// 后续的类型解析由容器统一处理, 因此两种数据源共享同一套解析逻辑。
type DataSource interface {
	// ReadSheet 读取表数据(含表头行), 返回与excel GetRows 等价的二维字符串
	ReadSheet(req SheetReadRequest) ([][]string, error)
	// ReadConstantRows 读取常量表数据(没有时返回nil)
	ReadConstantRows(req SheetReadRequest) ([][]string, error)
	// FileDigest 文件摘要(用于热更新判断)
	FileDigest(req SheetReadRequest) (string, error)
	// IsSingleSource 是否"一张表只对应一个数据文件"(json为true, excel分表时为false)
	IsSingleSource() bool
}

// NewDataSource 创建数据源(excel实现)
func NewDataSource() DataSource {
	return &excelDataSource{}
}

// excelDataSource 基于excelize的excel数据源
type excelDataSource struct{}

// ReadSheet 读取工作薄首个sheet的数据
func (d *excelDataSource) ReadSheet(req SheetReadRequest) ([][]string, error) {
	workbook, err := excelize.OpenFile(filepath.Join(req.RootPath, req.FileName))
	if err != nil {
		return nil, err
	}
	defer func() { _ = workbook.Close() }()
	sheetNames := workbook.GetSheetList()
	if len(sheetNames) == 0 {
		return nil, nil
	}
	return workbook.GetRows(sheetNames[0])
}

// ReadConstantRows 读取常量工作薄
func (d *excelDataSource) ReadConstantRows(req SheetReadRequest) ([][]string, error) {
	if req.ConstSheetName == "" {
		return nil, nil
	}
	workbook, err := excelize.OpenFile(filepath.Join(req.RootPath, req.FileName))
	if err != nil {
		return nil, err
	}
	defer func() { _ = workbook.Close() }()
	return workbook.GetRows(req.ConstSheetName)
}

// FileDigest 计算excel文件的md5
func (d *excelDataSource) FileDigest(req SheetReadRequest) (string, error) {
	rawData, err := os.ReadFile(filepath.Join(req.RootPath, req.FileName))
	if err != nil {
		return "", err
	}
	sum := md5.Sum(rawData)
	return hex.EncodeToString(sum[:]), nil
}

// IsSingleSource excel分表时一个容器对应多个文件
func (d *excelDataSource) IsSingleSource() bool {
	return false
}