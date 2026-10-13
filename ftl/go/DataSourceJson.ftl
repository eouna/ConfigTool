package ${packageName}

// 本文件由配置表工具生成: 数据源读取实现(json)
//
// 读取工具导出的 <表名>.json, 并还原成与excel一致的"字符串表格",
// 这样容器里的类型解析(list/set/map/date/enum...)可以完全复用, 不需要为json单独写一套解析。
//
// 数据格式: { "<id>": { "字段名": 值, ... }, ... }  (key为"0"的是枚举定义, 不是数据行)

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
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

// NewDataSource 创建数据源(json实现)
func NewDataSource() DataSource {
	return &jsonDataSource{}
}

// jsonDataSource 基于标准库 encoding/json 的json数据源
type jsonDataSource struct{}

// ReadSheet 读取 <表名>.json 并还原成字符串表格
func (d *jsonDataSource) ReadSheet(req SheetReadRequest) ([][]string, error) {
	fullPath := filepath.Join(req.RootPath, req.SheetName+".json")
	rawData, err := os.ReadFile(fullPath)
	if err != nil {
		return nil, err
	}
	var rawMap map[string]any
	if err = json.Unmarshal(rawData, &rawMap); err != nil {
		return nil, fmt.Errorf("解析json失败: %s: %w", fullPath, err)
	}

	colCount := len(req.Fields)
	headRowCount := req.DataStartRow
	for _, idx := range []int{req.NameRow, req.TypeRow, req.RangeRow} {
		if idx+1 > headRowCount {
			headRowCount = idx + 1
		}
	}
	if headRowCount < 0 {
		headRowCount = 0
	}
	rows := make([][]string, headRowCount, headRowCount+len(rawMap))
	for i := range rows {
		rows[i] = make([]string, colCount)
	}
	// 还原表头: 字段名行 + 类型行(数据范围行留空, 即不跳过任何列)
	for col, field := range req.Fields {
		if req.NameRow >= 0 && req.NameRow < len(rows) {
			rows[req.NameRow][col] = field.Name
		}
		if req.TypeRow >= 0 && req.TypeRow < len(rows) {
			rows[req.TypeRow][col] = field.RawType
		}
	}

	// 数据行按ID排序, 保证每次加载结果稳定
	ids := make([]string, 0, len(rawMap))
	for key := range rawMap {
		if isEnumHolderKey(key) {
			continue
		}
		ids = append(ids, key)
	}
	sort.Slice(ids, func(i, j int) bool { return lessId(ids[i], ids[j]) })

	for _, id := range ids {
		item, ok := rawMap[id].(map[string]any)
		if !ok {
			continue
		}
		row := make([]string, colCount)
		hasValue := false
		for col, field := range req.Fields {
			value, exists := item[field.Name]
			if !exists || value == nil {
				continue
			}
			row[col] = valueToConfigString(field.RawType, value)
			hasValue = true
		}
		if !hasValue {
			continue
		}
		rows = append(rows, row)
	}
	return rows, nil
}

// ReadConstantRows 读取常量表(<常量表名>.json, 由工具按excel行列布局导出)
//
// 常量表在excel中单独维护, 导出json时会一并导出为 <常量表名>.json, 因此json数据源同样能读到常量;
// 文件不存在时返回nil(视为没有常量表), 常量字段保持默认值。
func (d *jsonDataSource) ReadConstantRows(req SheetReadRequest) ([][]string, error) {
	fullPath := filepath.Join(req.RootPath, req.SheetName+".json")
	rawData, err := os.ReadFile(fullPath)
	if err != nil {
		return nil, nil
	}
	var rawRows []any
	if err = json.Unmarshal(rawData, &rawRows); err != nil {
		return nil, fmt.Errorf("解析常量表json失败: %s: %w", fullPath, err)
	}
	rows := make([][]string, 0, len(rawRows))
	for _, rawRow := range rawRows {
		cells, ok := rawRow.([]any)
		if !ok {
			continue
		}
		row := make([]string, 0, len(cells))
		for _, cell := range cells {
			row = append(row, scalarToString(cell))
		}
		rows = append(rows, row)
	}
	return rows, nil
}

// FileDigest 计算json文件的md5
func (d *jsonDataSource) FileDigest(req SheetReadRequest) (string, error) {
	rawData, err := os.ReadFile(filepath.Join(req.RootPath, req.SheetName+".json"))
	if err != nil {
		return "", err
	}
	sum := md5.Sum(rawData)
	return hex.EncodeToString(sum[:]), nil
}

// IsSingleSource json下一个容器只读一个 <表名>.json
func (d *jsonDataSource) IsSingleSource() bool {
	return true
}

// valueToConfigString 把json值还原成"配置表中的字符串表示", 以便复用容器里的解析逻辑
func valueToConfigString(rawType string, value any) string {
	if value == nil {
		return ""
	}
	raw := strings.TrimSpace(rawType)

	// 时间: date<格式>
	if matched := dateTypeRe.FindStringSubmatch(raw); matched != nil {
		text, ok := value.(string)
		if !ok {
			return fmt.Sprint(value)
		}
		if parsed, ok := parseDateText(text); ok {
			return parsed.Format(javaDateLayoutToGo(matched[1]))
		}
		return text
	}
	// 列表 / 集合
	if matched := listTypeRe.FindStringSubmatch(raw); matched != nil {
		return joinValues(matched[1], matched[2], value)
	}
	if matched := setTypeRe.FindStringSubmatch(raw); matched != nil {
		return joinValues(matched[1], matched[2], value)
	}
	// 键值对: map<键类型{键值分隔符}值类型>{条目分隔符}
	if matched := mapTypeRe.FindStringSubmatch(raw); matched != nil {
		return joinMapValues(matched[1], matched[2], value)
	}
	// 基础类型 / 枚举
	return scalarToString(value)
}

// joinValues 列表/集合: 元素用分隔符连接
func joinValues(subType string, delimiter string, value any) string {
	items, ok := value.([]any)
	if !ok {
		return scalarToString(value)
	}
	parts := make([]string, 0, len(items))
	for _, item := range items {
		parts = append(parts, valueToConfigString(subType, item))
	}
	return strings.Join(parts, delimiter)
}

// joinMapValues 键值对: "键,值;键,值"
func joinMapValues(innerType string, entryDelimiter string, value any) string {
	inner := mapInnerRe.FindStringSubmatch(innerType)
	if inner == nil {
		return scalarToString(value)
	}
	keyType := inner[1]
	keyValueDelimiter := inner[2]
	valueType := inner[3]
	entries, ok := value.(map[string]any)
	if !ok {
		return scalarToString(value)
	}
	keys := make([]string, 0, len(entries))
	for key := range entries {
		keys = append(keys, key)
	}
	sort.Slice(keys, func(i, j int) bool { return lessId(keys[i], keys[j]) })
	parts := make([]string, 0, len(keys))
	for _, key := range keys {
		// json的key本身就是配置里的原始键文本(int等类型直接用文本即可)
		keyText := key
		if _, err := strconv.ParseFloat(key, 64); err == nil {
			keyText = key
		} else if strings.EqualFold(strings.TrimSpace(keyType), "string") {
			keyText = key
		}
		parts = append(parts, keyText+keyValueDelimiter+valueToConfigString(valueType, entries[key]))
	}
	return strings.Join(parts, entryDelimiter)
}

// scalarToString 基础类型/枚举转字符串
func scalarToString(value any) string {
	switch typed := value.(type) {
	case string:
		return typed
	case bool:
		return strconv.FormatBool(typed)
	case float64:
		return strconv.FormatFloat(typed, 'f', -1, 64)
	case json.Number:
		return typed.String()
	default:
		return fmt.Sprint(typed)
	}
}

// parseDateText 兼容几种常见的日期文本
func parseDateText(text string) (time.Time, bool) {
	layouts := []string{
		time.RFC3339,
		"2006-01-02T15:04:05-0700",
		"2006-01-02T15:04:05Z0700",
		"2006-01-02 15:04:05",
		"2006-01-02 15:04",
		"2006-01-02",
	}
	for _, layout := range layouts {
		if parsed, err := time.Parse(layout, text); err == nil {
			return parsed, true
		}
	}
	return time.Time{}, false
}

// javaDateLayoutToGo java日期格式转go布局(支持 yyyy MM dd HH mm ss)
func javaDateLayoutToGo(javaLayout string) string {
	replacer := strings.NewReplacer(
		"yyyy", "2006",
		"MM", "01",
		"dd", "02",
		"HH", "15",
		"mm", "04",
		"ss", "05")
	return replacer.Replace(javaLayout)
}

// isEnumHolderKey json中key为0的是枚举定义
func isEnumHolderKey(key string) bool {
	return strings.TrimSpace(key) == "0"
}

// lessId 数字ID按数值比较, 其它按字符串比较
func lessId(left string, right string) bool {
	leftNum, leftErr := strconv.Atoi(left)
	rightNum, rightErr := strconv.Atoi(right)
	if leftErr == nil && rightErr == nil {
		return leftNum < rightNum
	}
	return left < right
}