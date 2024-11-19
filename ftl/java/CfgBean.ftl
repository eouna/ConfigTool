package ${packageName};

import java.util.*;

import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName ${dataStruct.fileName}
 * @sheetName ${dataStruct.sheetName}
 * @author Auto.Generator
 * @date ${date}
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ${beanClassName} extends ${parentClass} {

  /** 配置表名 */
  public static final String EXCEL_NAME = "${dataStruct.fileName}";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "${dataStruct.sheetName}";

<#list dataStruct.excelFieldInfoList as excelFieldInfo>
  /** ${excelFieldInfo.fieldDesc.fieldData} */
  protected ${excelFieldInfo.fieldType.fieldData} ${excelFieldInfo.fieldName.fieldData};
</#list>
<#list dataStruct.excelFieldInfoList as excelFieldInfo>

  /** 返回${excelFieldInfo.fieldDesc.fieldData} */
  public ${excelFieldInfo.fieldType.fieldData} get${excelFieldInfo.fieldName.fieldData?cap_first}() {
    return ${excelFieldInfo.fieldName.fieldData};
  }
</#list>
<#list dataStruct.excelEnumFieldInfoList as excelEnumFieldInfo>

  public enum ${excelEnumFieldInfo.enumClassName?cap_first} {
    // enum auto gen
    <#list excelEnumFieldInfo.enumFieldData as enumName>
    ${enumName},
    </#list>
    ;

    public static ${excelEnumFieldInfo.enumClassName?cap_first} getEnumByStr(String str) {
      for (${excelEnumFieldInfo.enumClassName?cap_first} value : values()) {
        if (value.name().equalsIgnoreCase(str)) {
          return value;
        }
      }
      throw new RuntimeException("${dataStruct.fileName} field ${excelEnumFieldInfo.enumClassName?cap_first} not found enum by str: " + str);
    }
  }
</#list>
}
