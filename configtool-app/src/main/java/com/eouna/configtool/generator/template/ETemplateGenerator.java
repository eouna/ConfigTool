package com.eouna.configtool.generator.template;

import com.eouna.configtool.generator.template.java.JavaTemplateGenerator;
import com.eouna.configtool.generator.template.java.JavaTemplateHandler;
import com.eouna.configtool.generator.template.cpp.CppTemplateGenerator;
import com.eouna.configtool.generator.template.cpp.CppTemplateHandler;
import com.eouna.configtool.generator.template.csharp.CSharpTemplateGenerator;
import com.eouna.configtool.generator.template.csharp.CSharpTemplateHandler;
import com.eouna.configtool.generator.template.go.GoTemplateGenerator;
import com.eouna.configtool.generator.template.go.GoTemplateHandler;
import com.eouna.configtool.generator.template.json.JsonTemplateGenerator;
import com.eouna.configtool.generator.template.lua.LuaTemplateGenerator;
import com.eouna.configtool.generator.template.lua.LuaTemplateHandler;
import com.eouna.configtool.generator.template.rust.RustTemplateGenerator;
import com.eouna.configtool.generator.template.rust.RustTemplateHandler;
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
  /** golang生成器 */
  GO_GENERATOR(GoTemplateHandler.getInstance(), GoTemplateGenerator.getInstance()),
  /** rust生成器 */
  RUST_GENERATOR(RustTemplateHandler.getInstance(), RustTemplateGenerator.getInstance()),
  /** C#生成器 */
  CSHARP_GENERATOR(CSharpTemplateHandler.getInstance(), CSharpTemplateGenerator.getInstance()),
  /** lua生成器 */
  LUA_GENERATOR(LuaTemplateHandler.getInstance(), LuaTemplateGenerator.getInstance()),
  /** C++生成器 */
  CPP_GENERATOR(CppTemplateHandler.getInstance(), CppTemplateGenerator.getInstance()),
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
