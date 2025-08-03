package com.eouna.configtool.generator.template;

import com.eouna.configtool.generator.template.java.JavaTemplateGenerator;
import com.eouna.configtool.generator.template.java.JavaTemplateHandler;
import com.eouna.configtool.generator.template.json.JsonTemplateGenerator;
import com.eouna.configtool.generator.template.json.JsonTemplateHandler;

/**
 * 模板生成器枚举
 *
 * @author CCL
 * @date 2023/3/10
 */
public enum ETemplateGenerator {
  /** java生成器 */
  JAVA_GENERATOR(JavaTemplateHandler.getInstance(), JavaTemplateGenerator.getInstance()),
  /** json生成器 */
  JSON_GENERATOR(JsonTemplateHandler.getInstance(), JsonTemplateGenerator.getInstance()),
  ;
  /** 模板处理handler */
  private final AbstractTemplateHandler templateHandler;
  /** 模板生成器 */
  private final AbstractTemplateGenerator templateGenerator;

  ETemplateGenerator(
      AbstractTemplateHandler templateHandler, AbstractTemplateGenerator templateGenerator) {
    this.templateHandler = templateHandler;
    this.templateGenerator = templateGenerator;
  }

  public AbstractTemplateHandler getTemplateHandler() {
    return templateHandler;
  }

  public AbstractTemplateGenerator getTemplateGenerator() {
    return templateGenerator;
  }

  public static ETemplateGenerator getTemplateGeneratorByType(String typeStr) {
    for (ETemplateGenerator value : values()) {
      if (value.getTemplateHandler().getTemplateBindRelatedPath().equals(typeStr)) {
        return value;
      }
    }
    throw new RuntimeException("暂不支持的类型生成器: " + typeStr + " 请在模板生成器模板枚举中添加对应的类型");
  }
}
