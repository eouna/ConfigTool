package com.eouna.configtool.generator.template.go;

import com.eouna.configtool.generator.template.AbstractTemplateHandler;

/**
 * golang模板处理类
 *
 * @author CCL
 */
public class GoTemplateHandler extends AbstractTemplateHandler {

  @Override
  public String getFileIdentifier() {
    return ".go";
  }

  @Override
  public String getTemplateBindRelatedPath() {
    return "go";
  }

  /**
   * 单例
   *
   * @return GoTemplateHandler
   */
  public static GoTemplateHandler getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final GoTemplateHandler instance;

    Singleton() {
      this.instance = new GoTemplateHandler();
    }

    public GoTemplateHandler getInstance() {
      return instance;
    }
  }
}