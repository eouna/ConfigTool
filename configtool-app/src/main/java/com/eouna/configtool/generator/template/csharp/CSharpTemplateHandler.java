package com.eouna.configtool.generator.template.csharp;

import com.eouna.configtool.generator.template.AbstractTemplateHandler;

/**
 * C#模板处理类
 *
 * @author CCL
 */
public class CSharpTemplateHandler extends AbstractTemplateHandler {

  @Override
  public String getFileIdentifier() {
    return ".cs";
  }

  @Override
  public String getTemplateBindRelatedPath() {
    return "csharp";
  }

  public static CSharpTemplateHandler getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final CSharpTemplateHandler instance;

    Singleton() {
      this.instance = new CSharpTemplateHandler();
    }

    public CSharpTemplateHandler getInstance() {
      return instance;
    }
  }
}
