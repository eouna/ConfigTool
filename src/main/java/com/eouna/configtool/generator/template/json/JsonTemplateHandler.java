package com.eouna.configtool.generator.template.json;

import com.eouna.configtool.generator.template.AbstractTemplateHandler;

/**
 * lua模板文件处理器
 *
 * @author CCL
 * @date 2023/3/3
 */
public class JsonTemplateHandler extends AbstractTemplateHandler {

  @Override
  public String getFileIdentifier() {
    return ".json";
  }

  @Override
  public String getTemplateBindRelatedPath() {
    return "json";
  }

  /**
   * 单例
   *
   * @return JsonTemplateHandler
   */
  public static JsonTemplateHandler getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final JsonTemplateHandler instance;

    Singleton() {
      this.instance = new JsonTemplateHandler();
    }

    public JsonTemplateHandler getInstance() {
      return instance;
    }
  }
}
