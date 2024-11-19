package com.eouna.configtool.core.boot.convert;
/**
 * 程序默认的转换器
 *
 * @author CCL
 * @date 2023/7/17
 */
public class ApplicationConverters {

  private static volatile ApplicationConverters instance;

  public ApplicationConverters() {
    loadDefaultConverters();
  }

  public static ApplicationConverters getInstance() {
    ApplicationConverters applicationConverters = ApplicationConverters.instance;
    if (applicationConverters == null) {
      synchronized (ApplicationConverters.class) {
        applicationConverters = ApplicationConverters.instance;
        if (applicationConverters == null) {
          applicationConverters = new ApplicationConverters();
          ApplicationConverters.instance = applicationConverters;
        }
      }
    }
    return applicationConverters;
  }

  public void loadDefaultConverters() {
    // TODO do something
  }
}
