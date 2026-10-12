package ${containerPackageName};

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import ${beanPackageName}.${beanClassName};

/**
 * ${excelName}配置管理容器
 *
 * @excelName ${excelName}
 * @sheetName ${sheetBean.sheetName}
 * @author CCL
 * @date ${date}
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ${containerClassName} extends ${parentClassName}<${beanClassName}> {
  <#list constantFields as constField>

  /** ${constField.fieldDesc.fieldData} */
  private ${constField.fieldType.fieldData} ${constField.fieldName.fieldData};
  </#list>
  <#list constantFields as constField>

  /** ${constField.fieldDesc.fieldData} */
  public ${constField.fieldType.fieldData} get${constField.fieldName.fieldData?cap_first}() {
    return ${constField.fieldName.fieldData};
  }
  </#list>

  @Override
  public boolean hasRelatedTable() {
    return ${hasRelatedTable?c};
  }

  @Override
  public boolean isParentConfigNode() {
    return ${isParentNode?c};
  }

  @Override
  public String getSheetName() {
    return "${sheetBean.sheetName}";
  }

  @Override
  public List<${dataSourcePackageName}.ConfigDataSource.FieldMeta> getFieldMetas() {
    List<${dataSourcePackageName}.ConfigDataSource.FieldMeta> fieldMetas = new ArrayList<>();
<#list fieldMetas as meta>
    fieldMetas.add(new ${dataSourcePackageName}.ConfigDataSource.FieldMeta("${meta.name}", "${meta.rawType}"));
</#list>
    return fieldMetas;
  }

  @Override
  public ${containerClassName} getNewContainer() {
    return new ${containerClassName}();
  }

  public ${containerClassName}() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    <#list bindExcelList as excelName>
    excelNameList.add("${excelName}");
    </#list>
    return excelNameList;
  }

  @Override
  protected ${beanClassName} createNewBean() {
    return new ${beanClassName}();
  }
}
