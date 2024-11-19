package com.eouna.configtool.core.annotaion;

import java.lang.annotation.*;

/**
 * @author CCL {@see org.springframework.core.Order}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface Order {

  /** The order value. */
  int value() default Integer.MIN_VALUE;
}
