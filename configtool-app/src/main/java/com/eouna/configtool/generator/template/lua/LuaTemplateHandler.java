package com.eouna.configtool.generator.template.lua;

import com.eouna.configtool.generator.template.AbstractTemplateHandler;

/**
 * Lua模板处理类
 *
 * @author CCL
 */
public class LuaTemplateHandler extends AbstractTemplateHandler {

  @Override
  public String getFileIdentifier() {
    return ".lua";
  }

  @Override
  public String getTemplateBindRelatedPath() {
    return "lua";
  }

  public static LuaTemplateHandler getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    INSTANCE;

    private final LuaTemplateHandler instance;

    Singleton() {
      this.instance = new LuaTemplateHandler();
    }

    public LuaTemplateHandler getInstance() {
      return instance;
    }
  }
}