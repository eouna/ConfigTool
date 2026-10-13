package ${packageName}

import (
	"fmt"
	"path/filepath"
	"reflect"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// CfgBean 配置Bean接口
type CfgBean interface {
	GetId() int32
}

var (
	// dateTypeRe 时间类型表达式 date<yyyy-MM-dd HH:mm>
	dateTypeRe = regexp.MustCompile(`(?i)^date<(.*)>$`)
	// listTypeRe 列表表达式 list<int>{,} / list<int>{,10}
	listTypeRe = regexp.MustCompile(`(?i)^list<(.*)>\{(.)(\d*)\}$`)
	// setTypeRe 集合表达式 set<int>{,}
	setTypeRe = regexp.MustCompile(`(?i)^set<(.*)>\{(.)(\d*)\}$`)
	// mapTypeRe 键值对表达式 map<int{,}String>{;}
	mapTypeRe = regexp.MustCompile(`(?i)^map<(.*)>\{(.)(\d*)\}$`)
	// mapInnerRe 键值对内部结构 键类型{分隔符}值类型
	mapInnerRe = regexp.MustCompile(`^(.+?)\{(.)\}(.+)$`)
)

// BaseCfgContainer 配置表容器基类
//
// @author CCL
type BaseCfgContainer[T CfgBean] struct {
	// 字段描述行
	FieldDescRow int
	// 字段类型行
	FieldTypeRow int
	// 字段名行
	FieldNameRow int
	// 字段数值范围行
	FieldDataRangeRow int
	// 数据读取开始行数
	DataStartRow int

	// 常量字段名列
	ConstFieldNameRow int
	// 常量字段类型列
	ConstFieldTypeRow int
	// 常量字段值列
	ConstFieldDataVal int
	// 常量字段描述列
	ConstFieldDescRow int
	// 常量配置工作薄名
	ConstantSheetName string

	// 跳过列配置值(数据范围列匹配该值时跳过该列)
	SkipStr string

	// 当前容器对应的表名(工作薄名), json数据源按该名字读取 <表名>.json
	SheetName string

	// 字段元数据(字段名 + 配置表中的原始类型, 按列顺序), json数据源据此还原表头
	FieldMetas []FieldMeta

	// 是否关联其他表
	HasRelatedTable bool
	// 是否是父配置表
	IsParentConfigNode bool

	// CfgBeanMap key: 配置的ID, 配置的数据
	CfgBeanMap map[int32]T
	// excel文件的md5码
	Md5CacheMap map[string]string

	newBeanFunc     func() T
	excelNameList   []string
	constantTargets map[string]any
	// 数据源(excel/json, 由生成时选择的数据读取方式决定)
	dataSource DataSource
}

// newBaseCfgContainer 创建基础容器
func newBaseCfgContainer[T CfgBean]() BaseCfgContainer[T] {
	return BaseCfgContainer[T]{
		FieldDescRow:      ${fieldInfo.fieldDesc.configBindRow},
		FieldTypeRow:      ${fieldInfo.fieldType.configBindRow},
		FieldNameRow:      ${fieldInfo.fieldName.configBindRow},
		FieldDataRangeRow: ${fieldInfo.fieldDataRange.configBindRow},
		DataStartRow:      ${dataStartRow},
		ConstFieldNameRow: ${constFieldInfo.fieldName.configBindRow},
		ConstFieldTypeRow: ${constFieldInfo.fieldType.configBindRow},
		ConstFieldDataVal: ${constFieldInfo.fieldVal.configBindRow},
		ConstFieldDescRow: ${constFieldInfo.fieldDesc.configBindRow},
		ConstantSheetName: "${constantSheetName}",
		SkipStr:           "${skipStr}",
		CfgBeanMap:        map[int32]T{},
		Md5CacheMap:       map[string]string{},
		dataSource:        NewDataSource(),
	}
}

// Init 初始化容器
//
// sheetName 为当前表名(工作薄名), json数据源按 <sheetName>.json 读取数据
func (c *BaseCfgContainer[T]) Init(newBean func() T, excelNameList []string, sheetName string, fieldMetas []FieldMeta) {
	c.newBeanFunc = newBean
	c.excelNameList = excelNameList
	c.SheetName = sheetName
	c.FieldMetas = fieldMetas
}

// SetConstantTargets 设置常量字段名到目标字段指针的映射
func (c *BaseCfgContainer[T]) SetConstantTargets(targets map[string]any) {
	c.constantTargets = targets
}

// Get 根据ID获取配置
func (c *BaseCfgContainer[T]) Get(id int32) (T, bool) {
	var zero T
	if c.CfgBeanMap == nil {
		return zero, false
	}
	value, ok := c.CfgBeanMap[id]
	return value, ok
}

// LoadData 加载数据, resourceRootPath 为excel资源根路径
func (c *BaseCfgContainer[T]) LoadData(resourceRootPath string) error {
	if strings.TrimSpace(resourceRootPath) == "" {
		return fmt.Errorf("bind excel path list is empty")
	}
	if c.newBeanFunc == nil {
		return fmt.Errorf("container has not been initialized")
	}
	tempCfgMap := make(map[int32]T)
	md5Map := make(map[string]string)
	if c.dataSource == nil {
		c.dataSource = NewDataSource()
	}
	// json数据源下一张表只对应一个数据文件, 避免分表时重复读取
	sourceFiles := c.excelNameList
	if c.dataSource.IsSingleSource() && len(sourceFiles) > 1 {
		sourceFiles = sourceFiles[:1]
	}
	for _, excelName := range sourceFiles {
		// 数据源读取(与具体数据源无关, excel/json共用同一套解析)
		req := SheetReadRequest{
			RootPath:     resourceRootPath,
			FileName:     excelName,
			SheetName:    c.SheetName,
			Fields:       c.FieldMetas,
			NameRow:      c.FieldNameRow,
			TypeRow:      c.FieldTypeRow,
			RangeRow:     c.FieldDataRangeRow,
			DataStartRow: c.DataStartRow,
		}
		digest, err := c.dataSource.FileDigest(req)
		if err != nil {
			return err
		}
		md5Map[filepath.Base(excelName)] = digest

		rows, err := c.dataSource.ReadSheet(req)
		if err != nil {
			return err
		}
		if len(rows) > 0 {
			if err = c.loadSheetRows(rows, tempCfgMap); err != nil {
				return err
			}
		}
		// 常量字段
		if len(c.constantTargets) > 0 && c.ConstantSheetName != "" {
			constReq := req
			constReq.SheetName = c.ConstantSheetName
			constRows, constErr := c.dataSource.ReadConstantRows(constReq)
			if constErr == nil && len(constRows) > 1 {
				if err = c.loadConstantFields(constRows[1:]); err != nil {
					return err
				}
			}
		}
	}
	c.CfgBeanMap = tempCfgMap
	c.Md5CacheMap = md5Map
	return nil
}

// loadSheetRows 解析工作薄数据
func (c *BaseCfgContainer[T]) loadSheetRows(rows [][]string, cfgMap map[int32]T) error {
	if len(rows) <= c.FieldNameRow {
		return nil
	}
	nameRow := rows[c.FieldNameRow]
	skipCols := c.getSkipColumns(rows)
	colCount := len(nameRow)
	for rowIdx := c.DataStartRow; rowIdx < len(rows); rowIdx++ {
		row := rows[rowIdx]
		if isBlankRow(row) {
			continue
		}
		bean := c.newBeanFunc()
		beanValue := reflect.ValueOf(bean)
		if beanValue.Kind() == reflect.Ptr {
			beanValue = beanValue.Elem()
		}
		for col := 0; col < colCount; col++ {
			if _, skip := skipCols[col]; skip {
				continue
			}
			fieldName := getCell(nameRow, col)
			if fieldName == "" {
				continue
			}
			fieldValue, found := findFieldByName(beanValue, fieldName)
			if !found || !fieldValue.CanSet() {
				// 配置表中的字段未在bean中找到, 跳过
				continue
			}
			rawType := getCell(rows[c.FieldTypeRow], col)
			parsed, err := parseToType(rawType, getCell(row, col), fieldValue.Type())
			if err != nil {
				return fmt.Errorf("字段: %s 第 %d 行数据解析失败: %w", fieldName, rowIdx+1, err)
			}
			fieldValue.Set(parsed)
		}
		id := bean.GetId()
		if _, exists := cfgMap[id]; exists {
			return fmt.Errorf("出现重复的ID: %d", id)
		}
		cfgMap[id] = bean
	}
	return nil
}

// loadConstantFields 解析常量字段
func (c *BaseCfgContainer[T]) loadConstantFields(rows [][]string) error {
	for _, row := range rows {
		fieldName := getCell(row, c.ConstFieldNameRow)
		if fieldName == "" {
			continue
		}
		target, ok := c.constantTargets[fieldName]
		if !ok {
			// 配置中有, 代码没有的常量跳过
			continue
		}
		rawType := getCell(row, c.ConstFieldTypeRow)
		if rawType == "" {
			continue
		}
		if err := c.ParseConst(rawType, getCell(row, c.ConstFieldDataVal), target); err != nil {
			return fmt.Errorf("常量字段: %s 解析失败: %w", fieldName, err)
		}
	}
	return nil
}

// ParseConst 解析常量值到目标指针
func (c *BaseCfgContainer[T]) ParseConst(rawType string, text string, target any) error {
	targetValue := reflect.ValueOf(target)
	if targetValue.Kind() != reflect.Ptr || targetValue.IsNil() {
		return fmt.Errorf("constant target must be a non-nil pointer")
	}
	parsed, err := parseToType(rawType, text, targetValue.Elem().Type())
	if err != nil {
		return err
	}
	targetValue.Elem().Set(parsed)
	return nil
}

// getSkipColumns 获取需要跳过的列
func (c *BaseCfgContainer[T]) getSkipColumns(rows [][]string) map[int]struct{} {
	skipCols := map[int]struct{}{}
	if c.FieldDataRangeRow < 0 || c.FieldDataRangeRow >= len(rows) || c.SkipStr == "" {
		return skipCols
	}
	for col, cell := range rows[c.FieldDataRangeRow] {
		if strings.EqualFold(strings.TrimSpace(cell), c.SkipStr) {
			skipCols[col] = struct{}{}
		}
	}
	return skipCols
}

// parseToType 将excel中的字符串解析为目标Go类型
func parseToType(rawType string, text string, targetType reflect.Type) (reflect.Value, error) {
	raw := strings.TrimSpace(rawType)
	if raw == "" {
		return reflect.Zero(targetType), nil
	}
	if strings.TrimSpace(text) == "" {
		return reflect.Zero(targetType), nil
	}
	// 时间类型
	if matcher := dateTypeRe.FindStringSubmatch(raw); matcher != nil {
		layout := javaDateFormatToGoLayout(matcher[1])
		parsedTime, err := time.Parse(layout, strings.TrimSpace(text))
		if err != nil {
			return reflect.Value{}, fmt.Errorf("时间格式: %s 和数据源: %s 不匹配", matcher[1], text)
		}
		value := reflect.New(targetType).Elem()
		value.Set(reflect.ValueOf(parsedTime))
		return value, nil
	}
	// 列表类型
	if matcher := listTypeRe.FindStringSubmatch(raw); matcher != nil {
		return parseSequence(text, matcher[1], matcher[2], matcher[3], targetType, false)
	}
	// 集合类型
	if matcher := setTypeRe.FindStringSubmatch(raw); matcher != nil {
		return parseSequence(text, matcher[1], matcher[2], matcher[3], targetType, true)
	}
	// 键值对类型
	if matcher := mapTypeRe.FindStringSubmatch(raw); matcher != nil {
		return parseMapType(text, matcher[1], matcher[2], matcher[3], targetType)
	}
	// 基础类型/枚举
	return parseScalar(text, targetType)
}

// parseSequence 解析列表/集合
func parseSequence(text string, subType string, delimiter string, sizeLimit string, targetType reflect.Type, isSet bool) (reflect.Value, error) {
	if targetType.Kind() != reflect.Slice {
		return reflect.Value{}, fmt.Errorf("目标类型 %s 不是切片类型", targetType)
	}
	parts := strings.Split(text, delimiter)
	if err := checkSizeLimit(len(parts), sizeLimit); err != nil {
		return reflect.Value{}, err
	}
	result := reflect.MakeSlice(targetType, 0, len(parts))
	seen := make(map[string]struct{}, len(parts))
	for _, part := range parts {
		if strings.TrimSpace(part) == "" {
			continue
		}
		if isSet {
			if _, exists := seen[part]; exists {
				return reflect.Value{}, fmt.Errorf("Set列数据出现重复数据: %s", part)
			}
			seen[part] = struct{}{}
		}
		value, err := parseToType(subType, part, targetType.Elem())
		if err != nil {
			return reflect.Value{}, err
		}
		result = reflect.Append(result, value)
	}
	return result, nil
}

// parseMapType 解析键值对
func parseMapType(text string, inner string, entryDelimiter string, sizeLimit string, targetType reflect.Type) (reflect.Value, error) {
	if targetType.Kind() != reflect.Map {
		return reflect.Value{}, fmt.Errorf("目标类型 %s 不是map类型", targetType)
	}
	innerMatcher := mapInnerRe.FindStringSubmatch(inner)
	if innerMatcher == nil {
		return reflect.Value{}, fmt.Errorf("map类型描述错误: %s", inner)
	}
	keyType := innerMatcher[1]
	keyDelimiter := innerMatcher[2]
	valueType := innerMatcher[3]

	entries := strings.Split(text, entryDelimiter)
	if err := checkSizeLimit(len(entries), sizeLimit); err != nil {
		return reflect.Value{}, err
	}
	result := reflect.MakeMapWithSize(targetType, len(entries))
	for _, entry := range entries {
		if strings.TrimSpace(entry) == "" {
			continue
		}
		keyValuePair := strings.SplitN(entry, keyDelimiter, 2)
		if len(keyValuePair) < 2 {
			return reflect.Value{}, fmt.Errorf("map数据格式错误: %s", entry)
		}
		parsedKey, err := parseToType(keyType, keyValuePair[0], targetType.Key())
		if err != nil {
			return reflect.Value{}, err
		}
		parsedValue, err := parseToType(valueType, keyValuePair[1], targetType.Elem())
		if err != nil {
			return reflect.Value{}, err
		}
		result.SetMapIndex(parsedKey, parsedValue)
	}
	return result, nil
}

// parseScalar 解析基础类型
func parseScalar(text string, targetType reflect.Type) (reflect.Value, error) {
	value := reflect.New(targetType).Elem()
	trimmed := strings.TrimSpace(text)
	switch targetType.Kind() {
	case reflect.String:
		value.SetString(text)
	case reflect.Bool:
		parsed, err := strconv.ParseBool(strings.ToLower(trimmed))
		if err != nil {
			return reflect.Value{}, fmt.Errorf("布尔值解析失败: %s", text)
		}
		value.SetBool(parsed)
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		parsed, err := strconv.ParseInt(integerPart(trimmed), 10, targetType.Bits())
		if err != nil {
			return reflect.Value{}, fmt.Errorf("整数解析失败: %s", text)
		}
		value.SetInt(parsed)
	case reflect.Uint, reflect.Uint8, reflect.Uint16, reflect.Uint32, reflect.Uint64:
		parsed, err := strconv.ParseUint(integerPart(trimmed), 10, targetType.Bits())
		if err != nil {
			return reflect.Value{}, fmt.Errorf("无符号整数解析失败: %s", text)
		}
		value.SetUint(parsed)
	case reflect.Float32, reflect.Float64:
		parsed, err := strconv.ParseFloat(trimmed, targetType.Bits())
		if err != nil {
			return reflect.Value{}, fmt.Errorf("浮点数解析失败: %s", text)
		}
		value.SetFloat(parsed)
	default:
		return reflect.Value{}, fmt.Errorf("不支持的字段类型: %s", targetType)
	}
	return value, nil
}

// checkSizeLimit 检查元素数量是否与限制值严格一致
func checkSizeLimit(size int, sizeLimit string) error {
	if sizeLimit == "" {
		return nil
	}
	limit, err := strconv.Atoi(sizeLimit)
	if err != nil || limit <= 0 {
		return nil
	}
	if size != limit {
		return fmt.Errorf("字段对应的数据数量: %d 与限制值: %d 不一致", size, limit)
	}
	return nil
}

// integerPart 去除浮点数的小数部分
func integerPart(value string) string {
	if idx := strings.Index(value, "."); idx > 0 {
		return value[:idx]
	}
	return value
}

// javaDateFormatToGoLayout 将Java时间格式转换为Go的时间布局
func javaDateFormatToGoLayout(javaLayout string) string {
	replacer := strings.NewReplacer(
		"yyyy", "2006",
		"yy", "06",
		"MM", "01",
		"dd", "02",
		"HH", "15",
		"mm", "04",
		"ss", "05",
	)
	return replacer.Replace(javaLayout)
}

// findFieldByName 递归查找结构体中匹配cfg标签或字段名的字段
func findFieldByName(value reflect.Value, name string) (reflect.Value, bool) {
	if value.Kind() == reflect.Ptr {
		if value.IsNil() {
			return reflect.Value{}, false
		}
		value = value.Elem()
	}
	if value.Kind() != reflect.Struct {
		return reflect.Value{}, false
	}
	valueType := value.Type()
	for i := 0; i < valueType.NumField(); i++ {
		structField := valueType.Field(i)
		fieldValue := value.Field(i)
		if tag := structField.Tag.Get("cfg"); tag != "" && strings.EqualFold(tag, name) {
			return fieldValue, true
		}
		if structField.Anonymous {
			if found, ok := findFieldByName(fieldValue, name); ok {
				return found, true
			}
			continue
		}
		if strings.EqualFold(structField.Name, capitalize(name)) {
			return fieldValue, true
		}
	}
	return reflect.Value{}, false
}

// capitalize 首字母大写
func capitalize(value string) string {
	if value == "" {
		return value
	}
	return strings.ToUpper(value[:1]) + value[1:]
}

// getCell 安全读取单元格
func getCell(row []string, idx int) string {
	if idx < 0 || idx >= len(row) {
		return ""
	}
	return strings.TrimSpace(row[idx])
}

// isBlankRow 是否是空行
func isBlankRow(row []string) bool {
	for _, cell := range row {
		if strings.TrimSpace(cell) != "" {
			return false
		}
	}
	return true
}
