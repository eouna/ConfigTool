package com.eouna.configtool.generator.template.java;

import javax.tools.JavaFileObject.Kind;

import com.eouna.configtool.generator.template.AbstractTemplateHandler;

/**
 * java模板处理类
 *
 * @author CCL
 * @date 2023/3/3
 */
public class JavaTemplateHandler extends AbstractTemplateHandler {

  @Override
  public String getFileIdentifier() {
    return Kind.SOURCE.extension;
  }

  @Override
  public String getTemplateBindRelatedPath() {
    return Kind.SOURCE.extension.substring(1);
  }

  /**
   * 单例
   *
   * @return JavaTemplateHandler
   */
  public static JavaTemplateHandler getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final JavaTemplateHandler instance;

    Singleton() {
      this.instance = new JavaTemplateHandler();
    }

    public JavaTemplateHandler getInstance() {
      return instance;
    }
  }
}
