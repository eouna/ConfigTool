package com.eouna.configtool.generator.template.ts;

import com.eouna.configtool.generator.template.AbstractTemplateHandler;

/**
 * TypeScript模板处理类。
 *
 * <p>生成严格类型的配置读取代码, 数据统一通过JSON加载。
 *
 * @author CCL
 */
public class TsTemplateHandler extends AbstractTemplateHandler {

  @Override
  public String getFileIdentifier() {
    return ".ts";
  }

  @Override
  public String getTemplateBindRelatedPath() {
    return "ts";
  }

  public static TsTemplateHandler getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final TsTemplateHandler instance;

    Singleton() {
      this.instance = new TsTemplateHandler();
    }

    public TsTemplateHandler getInstance() {
      return instance;
    }
  }
}
