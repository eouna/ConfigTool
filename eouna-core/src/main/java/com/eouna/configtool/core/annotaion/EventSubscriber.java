package com.eouna.configtool.core.annotaion;

import java.lang.annotation.*;

/**
 * @author CCL
 * @version : [v1.0]
 * @className : [EventSubscriber]
 * @description :  事件订阅注解
 * @createTime : [2026/5/12 10:54]
 */
@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface EventSubscriber {
}
