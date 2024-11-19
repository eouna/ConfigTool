package com.eouna.configtool.core.boot.env;

import com.eouna.configtool.core.boot.convert.ApplicationConverters;

/**
 * 程序环境处理基类
 *
 * @author CCL
 * @date 2023/7/17
 */
public abstract class AbstractApplicationEnvironment implements IApplicationEnvironment{

	private ApplicationConverters applicationConverters;

	@Override
	public void setApplicationConvertors(ApplicationConverters applicationConverters) {
		this.applicationConverters = applicationConverters;
	}

	public ApplicationConverters getApplicationConverters() {
		return applicationConverters;
	}
}
