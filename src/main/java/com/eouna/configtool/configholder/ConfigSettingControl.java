package com.eouna.configtool.configholder;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.eouna.configtool.ui.component.IgnoreConfigBindField;
import javafx.scene.control.Control;

/**
 * 配置设置注解用于标示是否展示在设置界面
 *
 * @author CCL
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.FIELD})
public @interface ConfigSettingControl {

  /**
   * 描述 如果注解的字段类也注解了ConfigSettingControl则优先使用字段上饿描述作为显示
   *
   * @return 描述
   */
  String desc();

  /**
   * 输入框的提示
   *
   * @return 提示
   */
  String placeholder() default "";

  /**
   * 是否展示
   *
   * @return 是否展示
   */
  boolean show() default true;

  /**
   * 绑定的输入组件
   *
   * @return 输入组件
   */
  Class<? extends Control> bindComponent() default IgnoreConfigBindField.class;
}
