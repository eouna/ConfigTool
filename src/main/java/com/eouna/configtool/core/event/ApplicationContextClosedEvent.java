package com.eouna.configtool.core.event;

import com.eouna.configtool.core.context.AbstractApplicationContext;

/**
 * 程序上下文关闭事件
 *
 * @author CCL
 * @date 2023/9/19
 */
public class ApplicationContextClosedEvent extends ApplicationContextEvent{

	/**
	 * Constructs a prototypical Event.
	 *
	 * @param source the object on which the Event initially occurred
	 * @throws IllegalArgumentException if source is null
	 */
	public ApplicationContextClosedEvent(AbstractApplicationContext source) {
		super(source);
	}
}
