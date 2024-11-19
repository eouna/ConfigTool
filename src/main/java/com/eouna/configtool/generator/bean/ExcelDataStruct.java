package com.eouna.configtool.generator.bean;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.eouna.configtool.configholder.SystemConfigHolder;
import com.google.common.base.Objects;

/**
 * excel数据结构
 *
 * @author CCL
 * @date 2023/3/3
 */
public class ExcelDataStruct {

  /** excel文件名 */
  private String fileName;

  /** 工作薄名 */
  private String sheetName;

  /** excel字段信息列表 */
  private final Set<ExcelFieldInfo> excelFieldInfoList = new LinkedHashSet<>();
  /** excel枚举字段信息列表 */
  private final Set<ExcelEnumFieldInfo> excelEnumFieldInfoList = new LinkedHashSet<>();

  public ExcelDataStruct() {}

  public ExcelDataStruct(String fileName, String sheetName) {
    this.fileName = fileName;
    this.sheetName = sheetName;
  }

  public String getFileName() {
    return fileName;
  }

  public String getSheetName() {
    return sheetName;
  }

  public Set<ExcelFieldInfo> getExcelFieldInfoList() {
    return excelFieldInfoList;
  }

  public Set<ExcelEnumFieldInfo> getExcelEnumFieldInfoList() {
    return excelEnumFieldInfoList;
  }

  /**
   * 字段元组
   *
   * @param <T> t
   */
  public static class FieldMetadata<T> {
    /** 数据 */
    private T fieldData;
    /** 绑定的字符串 */
    private final int configBindRow;

    public FieldMetadata(T fieldData, int configBindRow) {
      this.fieldData = fieldData;
      this.configBindRow = configBindRow;
    }

    public FieldMetadata(int configBindRow) {
      this.configBindRow = configBindRow;
    }

    public void setFieldData(T fieldData) {
      this.fieldData = fieldData;
    }

    public T getFieldData() {
      return fieldData;
    }

    public int getConfigBindRow() {
      return configBindRow;
    }
  }

  /** excel 字段信息 */
  public static class ExcelFieldInfo {
    /** 字段描述 */
    protected FieldMetadata<String> fieldDesc;
    /** 字段类型 */
    protected FieldMetadata<String> fieldType;
    /** 字段名 */
    protected FieldMetadata<String> fieldName;
    /** 字段范围值 */
    protected FieldMetadata<String> fieldDataRange;

    public ExcelFieldInfo() {
      this.fieldDesc =
          new FieldMetadata<>(
              SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldDescRow());
      this.fieldType =
          new FieldMetadata<>(
              SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldTypeRow());
      this.fieldName =
          new FieldMetadata<>(
              SystemConfigHolder.getInstance().getExcelConf().getFieldRows().getFieldNameRow());
      this.fieldDataRange =
          new FieldMetadata<>(
              SystemConfigHolder.getInstance()
                  .getExcelConf()
                  .getFieldRows()
                  .getFieldDataRangeRow());
    }

    public FieldMetadata<String> getFieldDesc() {
      return fieldDesc;
    }

    public FieldMetadata<String> getFieldType() {
      return fieldType;
    }

    public FieldMetadata<String> getFieldName() {
      return fieldName;
    }

    public FieldMetadata<String> getFieldDataRange() {
      return fieldDataRange;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ExcelFieldInfo)) {
        return false;
      }
      ExcelFieldInfo that = (ExcelFieldInfo) o;
      return Objects.equal(getFieldDesc(), that.getFieldDesc())
          && Objects.equal(getFieldType(), that.getFieldType())
          && Objects.equal(getFieldName(), that.getFieldName())
          && Objects.equal(getFieldDataRange(), that.getFieldDataRange());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(getFieldDesc(), getFieldType(), getFieldName(), getFieldDataRange());
    }
  }

  /** excel 枚举字段信息 */
  public static class ExcelEnumFieldInfo extends ExcelFieldInfo {

    /** 枚举内的字段数据 */
    private Set<String> enumFieldData = new HashSet<>();

    private String enumClassName;

    public Set<String> getEnumFieldData() {
      return enumFieldData;
    }

    public ExcelEnumFieldInfo() {
      super();
    }

    public void setEnumFieldData(Set<String> enumFieldData) {
      this.enumFieldData = enumFieldData;
    }

    public String getEnumClassName() {
      return enumClassName;
    }

    public void setEnumClassName(String enumClassName) {
      this.enumClassName = enumClassName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ExcelEnumFieldInfo)) {
        return false;
      }
      if (!super.equals(o)) {
        return false;
      }
      ExcelEnumFieldInfo that = (ExcelEnumFieldInfo) o;
      return Objects.equal(getEnumFieldData(), that.getEnumFieldData());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(super.hashCode(), getEnumFieldData());
    }
  }
}
