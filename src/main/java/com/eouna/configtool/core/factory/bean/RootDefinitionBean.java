package com.eouna.configtool.core.factory.bean;

import java.util.List;

import com.eouna.configtool.core.factory.config.EBeanIdentifierScope;

/**
 * Description...
 *
 * @author CCL
 * @date 2023/11/3
 */
public class RootDefinitionBean extends AbstractBeanDefinition {

  @Override
  public void setParentName(String parentName) {}

  @Override
  public String getParentName() {
    return null;
  }

  @Override
  public EBeanIdentifierScope getScope() {
    return null;
  }

  @Override
  public void setDependOn(String... beanClassName) {}

  @Override
  public List<String> getDependOnList() {
    return null;
  }
}
