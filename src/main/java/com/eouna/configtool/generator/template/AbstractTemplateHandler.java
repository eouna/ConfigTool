package com.eouna.configtool.generator.template;

/**
 * 基础模板处理类
 *
 * @author CCL
 * @date 2023/3/3
 */
public abstract class AbstractTemplateHandler {

  /**
   * 获取文件标示符
   *
   * @return 文件标示符
   */
  public abstract String getFileIdentifier();

  /**
   * 获取选择器绑定的类型
   *
   * @return 返回类型
   */
  public abstract String getTemplateBindRelatedPath();
}
