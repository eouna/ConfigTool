package com.eouna.configtool.configholder;

import java.io.IOException;

import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.logger.TextAreaLogger;
import com.eouna.configtool.utils.ToolsLoggerUtils;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * 配置更新切面 LTW(load-time weaving) 新加切面时需要注册到META-INFO/aop.xml中
 *
 * @author CCL
 * @date 2023/4/11
 */
@Aspect
public class ConfigDataUpdateAspect {

  /** 注解了CfgDataBean的子类且方法名为set开头 */
  @Pointcut(
      value = "@target(com.eouna.configtool.configholder.CfgDataBean) && execution(* set*(..))")
  public void configSetProxy() {}

  @After(value = "configSetProxy()")
  public void setAfter() {
    TextAreaLogger textAreaLogger = ToolsLoggerUtils.getMainTextAreaLog();
    try {
      SystemConfigHolder.getInstance().saveSystemConfigToFile();
    } catch (IOException | IllegalAccessException e) {
      if (textAreaLogger != null) {
        textAreaLogger.error("更新配置失败", e);
      } else {
        LoggerUtils.getLogger().error("更新配置失败", e);
      }
    }
  }
}
