package com.eouna.configtool.configholder;

import com.eouna.configtool.ui.controllers.ConfigSettingController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标示配置bean类
 *
 * @author CCL
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface CfgDataBean {}
