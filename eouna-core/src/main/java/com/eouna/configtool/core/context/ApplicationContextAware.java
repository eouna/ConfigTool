package com.eouna.configtool.core.context;

import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.factory.Aware;

/**
 * @author : [程春林(Administrator)]
 * @version : [v1.0]
 * @className : [ApplicationContextAware]
 * @description : 应用上下文持有者
 * @createTime : [2026/5/6 20:10]
 */
public interface ApplicationContextAware extends Aware {

    /**
     * 设置应用上下文
     */
    void setApplicationContext(ApplicationContext applicationContext);
}
