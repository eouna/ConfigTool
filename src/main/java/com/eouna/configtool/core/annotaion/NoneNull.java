package com.eouna.configtool.core.annotaion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 不能为空注释
 *
 * @author CCL
 */
@Target(value = {ElementType.FIELD, ElementType.PARAMETER})
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
public @interface NoneNull {}
