package com.eouna.configtool.ui.component;

import com.eouna.configtool.configholder.ConfigDataBean;
import com.eouna.configtool.generator.template.ExcelFieldParseAdapter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TextField;

/**
 * 配置表绑定输入框
 *
 * @author CCL
 */
public class ConfigBindTextField extends TextField {

  /** 绑定的配置表名 绑定的根路径{@link ConfigDataBean} 通过绑定字段名的路径对其进行复制和取值 */
  public StringProperty bindConfigFieldPath = new SimpleStringProperty(this, "bindConfigName", "");

  public String getBindConfigFieldPath() {
    return bindConfigFieldPath.get();
  }

  public StringProperty bindConfigFieldPathProperty() {
    return bindConfigFieldPath;
  }

  public void setBindConfigFieldPath(String bindConfigFieldPath) {
    this.bindConfigFieldPath.set(bindConfigFieldPath);
  }

  /** 值适配器 */
  private final ObjectProperty<ESettingValAdapter> valAdapter =
      new SimpleObjectProperty<>(this, "valAdapter", ESettingValAdapter.STRING);

  public ESettingValAdapter getValAdapter() {
    return valAdapter.get();
  }

  public ObjectProperty<ESettingValAdapter> valAdapterProperty() {
    return valAdapter;
  }

  public void setValAdapter(ESettingValAdapter valAdapter) {
    this.valAdapter.set(valAdapter);
  }

  /** 设置值适配器 */
  public enum ESettingValAdapter {
    // 整数
    INT(ExcelFieldParseAdapter.INT),
    // Long
    LONG(ExcelFieldParseAdapter.LONG),
    // 字符串适配器
    STRING(ExcelFieldParseAdapter.STRING),
    // 浮点数适配器
    FLOAT(ExcelFieldParseAdapter.FLOAT),
    // 双精度浮点数适配器
    DOUBLE(ExcelFieldParseAdapter.DOUBLE),
    // bool适配器
    BOOLEAN(ExcelFieldParseAdapter.BOOLEAN),
    ;
    /** 字段解析适配器 */
    final ExcelFieldParseAdapter fieldParseAdapter;

    ESettingValAdapter(ExcelFieldParseAdapter fieldParseAdapter) {
      this.fieldParseAdapter = fieldParseAdapter;
    }

    public ExcelFieldParseAdapter getFieldParseAdapter() {
      return fieldParseAdapter;
    }
  }
}
