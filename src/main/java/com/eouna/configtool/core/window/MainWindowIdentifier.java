package com.eouna.configtool.core.window;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author CCL
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface MainWindowIdentifier {

  /**
   * 是否多开
   *
   * @return 多开
   */
  boolean isMultiWindow() default false;
}
