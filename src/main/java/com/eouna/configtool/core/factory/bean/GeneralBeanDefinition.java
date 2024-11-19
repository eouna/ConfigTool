package com.eouna.configtool.core.factory.bean;

import java.util.List;

import com.eouna.configtool.core.factory.config.EBeanIdentifierScope;

/**
 * Description...
 *
 * @author CCL
 * @date 2023/9/22
 */
public class GeneralBeanDefinition extends AbstractBeanDefinition {
	@Override
	public void setParentName(String parentName) {

	}

	@Override
	public String getParentName() {
		return null;
	}

	@Override
	public EBeanIdentifierScope getScope() {
		return EBeanIdentifierScope.SINGLETON;
	}

	@Override
	public void setDependOn(String... beanClassName) {

	}

	@Override
	public List<String> getDependOnList() {
		return null;
	}
}
