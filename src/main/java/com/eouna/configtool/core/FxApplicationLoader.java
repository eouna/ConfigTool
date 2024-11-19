package com.eouna.configtool.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.eouna.configtool.core.annotaion.FxApplication;
import com.eouna.configtool.core.boot.ApplicationBootListenersHolder;
import com.eouna.configtool.core.boot.FxApplicationShutdownHook;
import com.eouna.configtool.core.boot.IApplicationBootListener;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.boot.context.IApplicationContextInitializer;
import com.eouna.configtool.core.boot.convert.ApplicationConverters;
import com.eouna.configtool.core.boot.env.CommandLineAndArgs;
import com.eouna.configtool.core.boot.env.EnvironmentConstant;
import com.eouna.configtool.core.boot.env.EnvironmentConstant.StartMode;
import com.eouna.configtool.core.boot.env.FxApplicationEnvironment;
import com.eouna.configtool.core.boot.env.IApplicationEnvironment;
import com.eouna.configtool.core.boot.env.NoneGuiApplicationEnvironment;
import com.eouna.configtool.core.exceptions.InitConstructException;
import com.eouna.configtool.core.factory.FxApplicationClassLoader;
import com.eouna.configtool.core.context.AbstractApplicationContext;
import com.eouna.configtool.core.context.AnnotationApplicationContext;
import com.eouna.configtool.core.context.ApplicationListener;
import com.eouna.configtool.core.context.GenericApplicationContext;
import com.eouna.configtool.core.factory.support.AbstractAutowireBeanFactory;
import com.eouna.configtool.core.factory.support.AutowireBeanFactory;
import com.eouna.configtool.core.io.FxApplicationFactoriesLoader;
import com.eouna.configtool.core.utils.BeanUtils;
import com.eouna.configtool.core.utils.ClassUtils;
import com.eouna.configtool.core.watcher.TimeConsumeWatcher;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 程序加载器
 *
 * @author CCL
 * @date 2023/3/1
 */
public class FxApplicationLoader extends Application {

  /** logger */
  private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

  /** 主类 */
  private final Class<?> mainClass;

  /** 是否允许程序中的bean进行循环应用 */
  private boolean isAllowCircleReference;

  /** 使用指定的classloader加载 */
  private ClassLoader classLoader;

  /** 初始化进入时的主界面 */
  private final Stage initStage;

  /** 程序内部初始监听器 */
  private final List<ApplicationListener<?>> listeners = new CopyOnWriteArrayList<>();

  /** 程序关闭钩子 */
  private static final FxApplicationShutdownHook APPLICATION_SHUTDOWN_HOOK =
      new FxApplicationShutdownHook();

  public static ApplicationContext run(Class<?> mainClass, Stage mainStage, Parameters parameters) {
    String[] args =
        parameters.getRaw().isEmpty() ? new String[0] : parameters.getRaw().toArray(new String[0]);
    return new FxApplicationLoader(mainClass, mainStage).run(args);
  }

  private FxApplicationLoader(Class<?> mainClass, Stage initStage) {
    if (mainClass == null) {
      this.mainClass = ClassUtils.getMainApplicationClass();
    } else {
      this.mainClass = mainClass;
    }
    this.initStage = initStage;
    // 初始化
    initApplicationBootListeners();
  }

  /** 初始化程序加载时的需要响应的监听器 用于容器加载完成之前的事件监听 容器本身产生的事件的响应 */
  private void initApplicationBootListeners() {
    List<ApplicationListener> applicationListeners =
        FxApplicationFactoriesLoader.loadFactories(
            ApplicationListener.class, this.getClass().getClassLoader());
    for (ApplicationListener applicationListener : applicationListeners) {
      listeners.add(applicationListener);
    }
    logger.info(
        "容器注册容器内部监听器完成: [{}]",
        applicationListeners.stream()
            .map(listener -> listener.getClass().getSimpleName())
            .collect(Collectors.joining(",")));
  }

  /**
   * 程序运行接口
   *
   * @param args 运行参数
   * @return 程序上下文
   */
  private ApplicationContext run(String... args) {
    // 解析参数 java -mode=generate -excel_path=./ -dest_path=./
    TimeConsumeWatcher timeConsumeWatcher = new TimeConsumeWatcher();
    timeConsumeWatcher.start(this.getClass().getSimpleName());
    AbstractApplicationContext applicationContext = null;
    // 解析参数
    CommandLineAndArgs commandLineAndArgs = new CommandLineAndArgs(args);
    // 开始之前的工作
    // 1. 初始化容器初始化需要的监听器
    logger.info("容器开始初始化程序启动监听器");
    ApplicationBootListenersHolder applicationBootListenersHolder =
        initApplicationListener(commandLineAndArgs);
    logger.info("容器开始启动");
    applicationBootListenersHolder.starting();
    try {
      // 2. 广播程序加载开始之前的事件
      // 加载程序环境相关东西
      logger.info("容器开始初始化环境");
      IApplicationEnvironment environment = initApplicationEnvironment(commandLineAndArgs);
      environment.setApplicationConvertors(ApplicationConverters.getInstance());
      applicationContext = new AnnotationApplicationContext();
      // 3. 准备初始化context
      prepareContext(
          applicationContext, environment, commandLineAndArgs, applicationBootListenersHolder);
      logger.info("注册关闭钩子");
      // 初始话关闭钩子
      initRegisterHook(applicationContext);
      logger.info("容器开始刷新上下文");
      // 刷新上下文, 实例化 -> 对象 -> 属性填充 -> 初始化
      refreshContext(applicationContext);
      // 刷新之后
      afterRefreshContext(applicationContext);
      applicationBootListenersHolder.started(applicationContext);
      // 最后打开界面 调用界面初始化接口
      callRunner(applicationContext);
      return applicationContext;
    } catch (Exception e) {
      // 通知程序启动失败
      applicationBootListenersHolder.failed(applicationContext, e);
      if (logger.isTraceEnabled()) {
        logger.trace("程序启动失败", e);
      } else {
        logger.error("程序启动失败! reason: " + e.getMessage(), e);
      }
    }
    timeConsumeWatcher.prettyPrint();
    return null;
  }

  /** 获取基础需要扫描的包名 */
  private String[] getBaseScanPath() {
    if (!this.mainClass.isAnnotationPresent(FxApplication.class)) {
      throw new RuntimeException("初始化失败,主程序未添加注解 FxApplication ");
    }
    return this.mainClass.getAnnotation(FxApplication.class).componentScanPath();
  }

  /**
   * 初始化程序启动监听器
   *
   * @return 程序启动监听器持有者
   */
  private ApplicationBootListenersHolder initApplicationListener(CommandLineAndArgs args) {
    // 获取所有实现 IApplicationBootListener 接口的类
    Set<Class<?>> applicationBootListeners =
        FxApplicationClassLoader.getInterfaceImplClasses(
            this.getClass().getClassLoader(), IApplicationBootListener.class, getBaseScanPath());
    List<IApplicationBootListener> listeners = new ArrayList<>();
    try {
      for (Class<?> applicationBootListener : applicationBootListeners) {
        listeners.add(BeanUtils.getClassInstance(applicationBootListener, this, args));
      }
    } catch (InitConstructException e) {
      logger.error(e.getMessage(), e);
    }
    return new ApplicationBootListenersHolder(listeners);
  }

  /**
   * 初始化程序环境
   *
   * @param commandLineAndArgs 程序参数
   * @return 程序环境
   */
  private IApplicationEnvironment initApplicationEnvironment(
      CommandLineAndArgs commandLineAndArgs) {
    IApplicationEnvironment applicationEnvironment;
    List<String> arg =
        commandLineAndArgs.getOptionArg(EnvironmentConstant.COMMEND_LINE_ARG_START_MODE);
    if (arg.isEmpty()) {
      // 默认以带界面的方式进行启动
      applicationEnvironment = new FxApplicationEnvironment();
    } else {
      StartMode mode = StartMode.getModeByStr(arg.get(0));
      if (mode == null) {
        applicationEnvironment = new FxApplicationEnvironment();
      } else {
        switch (mode) {
          case DEFAULT_GUI:
            applicationEnvironment = new NoneGuiApplicationEnvironment();
            break;
          default:
            applicationEnvironment = new FxApplicationEnvironment();
        }
      }
    }
    return applicationEnvironment;
  }

  public List<ApplicationListener<?>> getListeners() {
    return listeners;
  }

  private void prepareContext(
      AbstractApplicationContext applicationContext,
      IApplicationEnvironment applicationEnvironment,
      CommandLineAndArgs commandLineAndArgs,
      ApplicationBootListenersHolder listenersHolder)
      throws InitConstructException {
    if (initStage != null) {
      applicationContext.setMainStage(initStage);
    }
    applicationContext.setEnvironment(applicationEnvironment);
    applicationContext.getBeanFactory().setConverter(ApplicationConverters.getInstance());
    logger.info("容器调用内部上下文初始化接口");
    // 注册和调用容器上下文的初始化接口
    callApplicationContextInitializer(applicationContext);
    logger.info("容器开始准备加载bean之前的工作");
    // 上下文准备加载bean之前
    listenersHolder.contextPrepared(applicationContext);
    AutowireBeanFactory beanFactory = applicationContext.getBeanFactory();
    beanFactory.registerSingletonBean("fxApplicationArgument", commandLineAndArgs);
    if (beanFactory instanceof AbstractAutowireBeanFactory) {
      ((AbstractAutowireBeanFactory) beanFactory).setAllCircleReference(isAllowCircleReference);
    }
  }

  /**
   * 初始程序关闭钩子
   *
   * @param applicationContext 程序上下文
   */
  private void initRegisterHook(AbstractApplicationContext applicationContext) {
    APPLICATION_SHUTDOWN_HOOK.registerApplicationContext(applicationContext);
  }

  /**
   * 注册程序关闭行为,用于非bean的关闭注册
   *
   * @param shutdownAction 关闭行为
   */
  public static void registerApplication(Runnable shutdownAction) {
    APPLICATION_SHUTDOWN_HOOK.getShutdownHooks().add(shutdownAction);
  }

  /**
   * 刷新上下文
   *
   * @param applicationContext 上下文
   */
  private void refreshContext(AbstractApplicationContext applicationContext) {
    applicationContext.refresh();
  }

  private void afterRefreshContext(AbstractApplicationContext applicationContext) {}

  private void callRunner(AbstractApplicationContext applicationContext) {}

  /**
   * 调用程序上下文初始化接口
   *
   * @param applicationContext 程序上下文
   * @throws InitConstructException e
   */
  private <T extends AbstractApplicationContext> void callApplicationContextInitializer(
      T applicationContext) throws InitConstructException {
    Set<Class<?>> initializerClasses =
        FxApplicationClassLoader.getInterfaceImplClasses(
            this.getClass().getClassLoader(),
            IApplicationContextInitializer.class,
            getBaseScanPath());
    for (Class<?> initializerClass : initializerClasses) {
      IApplicationContextInitializer<T> applicationContextInitializer =
          BeanUtils.getClassInstance(initializerClass);
      applicationContextInitializer.initial(applicationContext);
    }
  }

  @Override
  public void start(Stage primaryStage) throws Exception {
    // 初始化全局stage
    FxApplicationLoader.run(this.getClass(), primaryStage, getParameters());
  }

  @Override
  public void stop() throws Exception {
    super.stop();
  }

  public boolean isAllowCircleReference() {
    return isAllowCircleReference;
  }

  public void setAllowCircleReference(boolean allowCircleReference) {
    isAllowCircleReference = allowCircleReference;
  }
}
