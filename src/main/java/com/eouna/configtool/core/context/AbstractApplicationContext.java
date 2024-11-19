package com.eouna.configtool.core.context;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.boot.context.event.ApplicationEventDispatcher;
import com.eouna.configtool.core.boot.convert.ApplicationConverters;
import com.eouna.configtool.core.boot.env.IApplicationEnvironment;
import com.eouna.configtool.core.context.support.ApplicationContextAwareHooker;
import com.eouna.configtool.core.context.support.ApplicationListenerHooker;
import com.eouna.configtool.core.context.support.PostHookerRegistrationDelegate;
import com.eouna.configtool.core.event.ApplicationEvent;
import com.eouna.configtool.core.factory.config.BeanFactoryPostHooker;
import com.eouna.configtool.core.factory.config.BeanPostHooker;
import com.eouna.configtool.core.factory.support.AutowireBeanFactory;
import com.eouna.configtool.core.factory.support.DefaultBeanFactory;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 抽象程序上下文
 *
 * @author CCL
 * @date 2023/7/6
 */
public abstract class AbstractApplicationContext
    implements ApplicationContext, ApplicationEventPublisher {

  /** bean工厂 */
  protected DefaultBeanFactory factory;

  /** 事件派发器 */
  protected ApplicationEventDispatcher applicationEventDispatcher;

  /** 主舞台 */
  protected Stage mainStage;

  /** 是否处于激活状态 */
  private final AtomicBoolean active = new AtomicBoolean();

  /** 关闭钩子 */
  private Thread shutdownHooks = null;
  /** 开始时间 */
  private long startContextRefreshDate;
  /** 当前运行状态 true 为开始 false 关闭 */
  private final AtomicBoolean runStatus = new AtomicBoolean();
  /** 刷新前的程序监听器,主要是触发实现了初始化接口的类. */
  private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();
  /** 需要在刷新的时候运行的钩子 */
  private final List<BeanFactoryPostHooker> beanFactoryBeanPostHookers = new ArrayList<>();
  /** 关闭时添加的锁 */
  private Object shutdownLock = null;

  /** logger */
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public ApplicationEventDispatcher getEventDispatcher() {
    return applicationEventDispatcher;
  }

  public void setMainStage(Stage mainStage) {
    this.mainStage = mainStage;
  }

  @Override
  public Stage getMainStage() {
    return mainStage;
  }

  @Override
  public void setEnvironment(IApplicationEnvironment environment) {}

  @Override
  public IApplicationEnvironment getEnvironment() {
    return null;
  }

  @Override
  public void registerShutdownHook() {
    if (shutdownHooks == null) {
      this.shutdownHooks =
          new Thread(
              Thread.currentThread().getThreadGroup(),
              () -> {
                synchronized (shutdownLock) {
                  close();
                }
              },
              "Context-Shutdown-Hook");
      Runtime.getRuntime().addShutdownHook(this.shutdownHooks);
    }
  }

  public void addApplicationListener(ApplicationListener<? extends ApplicationEvent> listener) {
    if (applicationEventDispatcher != null) {
      applicationEventDispatcher.registerListeners(listener);
    }
    applicationListeners.add(listener);
  }

  public void removeApplicationListener(ApplicationListener<? extends ApplicationEvent> listener) {
    if (applicationEventDispatcher != null) {
      applicationEventDispatcher.removeListeners(listener);
    }
    applicationListeners.remove(listener);
  }

  public Set<ApplicationListener<?>> getApplicationListeners() {
    return applicationListeners;
  }

  /**
   * 刷新方法，主要作用:<br>
   * 刷新应用程序上下文。这意味着它会重新加载或重新初始化上下文中的所有 Bean 定义，重新创建 Bean 实例，重新解析属性，以及重新注册事件监听器等。<br>
   */
  public void refresh() {
    // 准备加载工作
    prepareRefresh();
    DefaultBeanFactory defaultBeanFactory = getRefreshedBeanFactory();
    // 准备bean工厂
    prepareBeanFactory(defaultBeanFactory);
    // 调用在bean工厂中注册的hookers
    PostHookerRegistrationDelegate.invokeBeanFactoryPostHookers(defaultBeanFactory, beanFactoryBeanPostHookers);
  }

  /**
   * 准备bean工厂
   *
   * @param beanFactory bean工厂
   */
  protected void prepareBeanFactory(DefaultBeanFactory beanFactory) {
    // 注册字段设置钩子,用于管理容器和其他组件之间的联系
    beanFactory.registerBeanPostHooker(new ApplicationContextAwareHooker(this));
    // 注册事件监听钩子,所有实现了ApplicationListener的类都将被注册到事件管理器中
    beanFactory.registerBeanPostHooker(new ApplicationListenerHooker(this));
  }

  /** 添加bean处理钩子 */
  public void addBeanFactoryPostHooker(BeanFactoryPostHooker beanPostHooker){
    beanFactoryBeanPostHookers.add(beanPostHooker);
  }

  /** 准备加载工作 */
  protected void prepareRefresh() {
    startContextRefreshDate = System.currentTimeMillis();
    runStatus.set(true);
  }

  protected DefaultBeanFactory getRefreshedBeanFactory() {
    refreshBeanFactory();
    return factory;
  }

  /** 刷新bean工程 */
  protected abstract void refreshBeanFactory();

  @Override
  public void close() {}

  @Override
  public AutowireBeanFactory getBeanFactory() {
    return factory;
  }

  @Override
  public void pushEvent(ApplicationEvent applicationEvent) {
    ApplicationContext.super.pushEvent(applicationEvent);
  }

  @Override
  public <T> T getBean(Class<?> beanClass) {
    return factory.getBean(beanClass);
  }

  @Override
  public <T> T getBean(String beanClassName) {
    return factory.getBean(beanClassName);
  }

  @Override
  public <T> T getBean(String beanClassName, Class<T> requiredType) {
    return null;
  }

  @Override
  public void unloadBean(Class<?> beanClass) {}

  @Override
  public void unloadBean(String beanClassName) {}

  @Override
  public void setConverter(ApplicationConverters converter) {}

  public boolean isActive() {
    return active.get();
  }

  public AtomicBoolean getActive() {
    return active;
  }

  @Override
  public void publishEvent(ApplicationEvent applicationEvent) {
    getEventDispatcher().dispatchEvent(applicationEvent);
  }

  @Override
  public <T extends EventObject> void publishEvent(T eventObject) {
    Objects.requireNonNull(eventObject, "事件对象不能为空");
    getEventDispatcher().dispatchEvent(new PayloadApplicationEvent<>(this, eventObject));
  }
}
