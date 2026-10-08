package com.eouna.configtool.generator.template.rust;

import com.eouna.configtool.generator.template.AbstractTemplateHandler;

/**
 * rust模板处理类
 *
 * @author CCL
 */
public class RustTemplateHandler extends AbstractTemplateHandler {

  @Override
  public String getFileIdentifier() {
    return ".rs";
  }

  @Override
  public String getTemplateBindRelatedPath() {
    return "rust";
  }

  /**
   * 单例
   *
   * @return RustTemplateHandler
   */
  public static RustTemplateHandler getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final RustTemplateHandler instance;

    Singleton() {
      this.instance = new RustTemplateHandler();
    }

    public RustTemplateHandler getInstance() {
      return instance;
    }
  }
}