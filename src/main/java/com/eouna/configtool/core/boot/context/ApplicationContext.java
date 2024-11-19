package com.eouna.configtool.core.boot.context;

import com.eouna.configtool.core.boot.context.event.ApplicationEventDispatcher;
import com.eouna.configtool.core.boot.context.event.ApplicationEventPusher;
import com.eouna.configtool.core.boot.convert.IConverter;
import com.eouna.configtool.core.boot.env.IApplicationEnvironment;
import com.eouna.configtool.core.factory.IBeanFactory;
import com.eouna.configtool.core.factory.support.AutowireBeanFactory;
import javafx.stage.Stage;

/**
 * @author CCL
 */
public interface ApplicationContext extends IBeanFactory, ApplicationEventPusher {

  /**
   * 获取事件分发器
   *
   * @return 事件分发器
   */
  ApplicationEventDispatcher getEventDispatcher();

  /**
   * 获取舞台
   *
   * @return 当前展示的舞台
   */
  Stage getMainStage();

  /**
   * 设置程序环境
   *
   * @param environment env
   */
  void setEnvironment(IApplicationEnvironment environment);

  /**
   * 获取程序环境
   *
   * @return 程序运行环境
   */
  IApplicationEnvironment getEnvironment();

  /**
   * 获取程序PID
   *
   * @return 程序启动PID
   */
  int getApplicationPid();

  /** 注册关闭钩子 */
  void registerShutdownHook();

  /**
   * 获取bean工厂
   *
   * @return bean工厂
   */
  AutowireBeanFactory getBeanFactory();

  /** 关闭 */
  void close();
}
