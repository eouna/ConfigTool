package com.eouna.configtool.core.factory.support;
/**
 * 抽象自动注入bean工厂
 *
 * @author CCL
 * @date 2023/9/15
 */
public abstract class AbstractAutowireBeanFactory extends AbstractBeanFactory
    implements AutowireBeanFactory {

  /** 是否允许循环引用 */
  private boolean allCircleReference;

  public boolean isAllCircleReference() {
    return allCircleReference;
  }

  public void setAllCircleReference(boolean allCircleReference) {
    this.allCircleReference = allCircleReference;
  }
}
