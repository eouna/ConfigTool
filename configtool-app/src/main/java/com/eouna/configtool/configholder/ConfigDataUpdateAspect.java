package com.eouna.configtool.configholder;

import com.eouna.configtool.core.logger.LoggerUtils;
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
    try {
      // 配置bean的setter统一回存配置文件
      SystemConfigHolder.getInstance().saveSystemConfigToFile();
    } catch (Throwable throwable) {
      // 保存失败(含容器未初始化)不影响调用方流程, 只记日志
      LoggerUtils.getLogger().error("更新配置失败", throwable);
    }
  }
}
