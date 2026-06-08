package com.eouna.configtool.configholder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段配置
 *
 * @author CCL
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface ConfigAutowired {

  /** 字段值 */
  String aliasName() default "";
}
