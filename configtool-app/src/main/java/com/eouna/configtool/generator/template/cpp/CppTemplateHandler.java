package com.eouna.configtool.generator.template.cpp;

import com.eouna.configtool.generator.template.AbstractTemplateHandler;

/**
 * C++模板处理类
 *
 * @author CCL
 */
public class CppTemplateHandler extends AbstractTemplateHandler {

  @Override
  public String getFileIdentifier() {
    return ".h";
  }

  @Override
  public String getTemplateBindRelatedPath() {
    return "cpp";
  }

  public static CppTemplateHandler getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final CppTemplateHandler instance;

    Singleton() {
      this.instance = new CppTemplateHandler();
    }

    public CppTemplateHandler getInstance() {
      return instance;
    }
  }
}
