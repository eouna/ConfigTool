package com.eouna.configtool.core.boot;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.eouna.configtool.core.event.ApplicationContextClosedEvent;
import com.eouna.configtool.core.context.AbstractApplicationContext;
import com.eouna.configtool.core.context.ApplicationListener;
import com.eouna.configtool.core.context.GenericApplicationContext;

/**
 * 程序关闭钩子
 *
 * @author CCL
 * @date 2023/9/19
 */
public class FxApplicationShutdownHook implements Runnable {

  /** 最大调用关闭接口的超时时间 */
  private static final long CLOSE_MAX_TIMEOUT = TimeUnit.MINUTES.toMillis(20);

  /** 关闭钩子线程注册标识 */
  private final AtomicBoolean shutdownHookThreadRegisterFlag = new AtomicBoolean(false);

  /** 程序上下文集 */
  private final List<AbstractApplicationContext> applicationContext = new ArrayList<>();
  /** 执行到关闭的上下文集合 */
  private final List<AbstractApplicationContext> closedApplicationContext = new ArrayList<>();
  /** 注册关闭的runnable */
  private final List<Runnable> shutdownHooks = new LinkedList<>();

  /** 关闭监听器,context注册此监听器,当程序关闭事件发生时,反向改变register中的contexts的注册值,并调用context的关闭事件 */
  private final ApplicationContextClosedListener applicationContextClosedListener =
      new ApplicationContextClosedListener();

  @Override
  public void run() {
    synchronized (this) {
      this.applicationContext.forEach(this::waitToClose);
      this.closedApplicationContext.forEach(this::waitToClose);
      this.shutdownHooks.forEach(Runnable::run);
    }
  }

  private void waitToClose(AbstractApplicationContext applicationContext) {
    if (!applicationContext.isActive()) {
      return;
    }
    applicationContext.close();
    long runOverTime = System.currentTimeMillis() + CLOSE_MAX_TIMEOUT;
    // 空转等待关闭逻辑完成
    while (System.currentTimeMillis() < runOverTime && applicationContext.isActive()) {}
  }

  public void registerApplicationContext(AbstractApplicationContext applicationContext) {
    if (shutdownHookThreadRegisterFlag.compareAndSet(false, true)) {
      Runtime.getRuntime()
          .removeShutdownHook(new Thread(this, FxApplicationShutdownHook.class.getName()));
    }
    synchronized (GenericApplicationContext.class) {
      this.applicationContext.add(applicationContext);
      applicationContext.addApplicationListener(applicationContextClosedListener);
    }
  }

  private class ApplicationContextClosedListener
      implements ApplicationListener<ApplicationContextClosedEvent> {

    @Override
    public void onEventHappen(ApplicationContextClosedEvent event) {
      synchronized (FxApplicationShutdownHook.class) {
        FxApplicationShutdownHook.this.applicationContext.remove(event.getApplicationContext());
        FxApplicationShutdownHook.this.closedApplicationContext.add(event.getApplicationContext());
      }
    }
  }

  public List<Runnable> getShutdownHooks() {
    return shutdownHooks;
  }
}
