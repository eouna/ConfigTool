package com.eouna.configtool.core.boot;

import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.boot.context.IApplicationContextInitializer;

/**
 * 程序加载的过程不同阶段的事件监听
 *
 * @author CCL
 */
public interface IApplicationBootListener {

  /** 初始化之前 场景初始化之前 */
  void staring();

  /**
   * 上下文加载之前 与{@linkplain IApplicationContextInitializer#initial(ApplicationContext)}<br>
   * 区别: 可以在整个容器生命周期生效,对容器框架之外的组件也生效
   *
   * @param context 程序运行上下文信息携带类
   */
  void contextLoadBefore(ApplicationContext context);

  /**
   * 上下文加载之后
   *
   * @param context 程序运行上下文信息携带类
   */
  void contextLoadAfter(ApplicationContext context);

  /**
   * 加载完成 场景初始化完成
   *
   * @param context 程序运行上下文信息携带类
   */
  void started(ApplicationContext context);

  /**
   * 运行中 场景展示之后
   *
   * @param context 程序运行上下文信息携带类
   */
  void running(ApplicationContext context);

  /**
   * 运行失败
   *
   * @param context 程序运行上下文信息携带类
   * @param throwable 失败异常
   */
  void failed(ApplicationContext context, Throwable throwable);
}
