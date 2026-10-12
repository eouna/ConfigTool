package com.eouna.configtool.generator.template.js;

import com.eouna.configtool.generator.template.AbstractTemplateHandler;

/**
 * JavaScript模板处理类
 *
 * <p>生成读取json数据的JavaScript代码。
 *
 * @author CCL
 */
public class JsTemplateHandler extends AbstractTemplateHandler {

  @Override
  public String getFileIdentifier() {
    return ".js";
  }

  @Override
  public String getTemplateBindRelatedPath() {
    return "js";
  }

  public static JsTemplateHandler getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final JsTemplateHandler instance;

    Singleton() {
      this.instance = new JsTemplateHandler();
    }

    public JsTemplateHandler getInstance() {
      return instance;
    }
  }
}