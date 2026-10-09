package com.eouna.configtool.boot;

import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.annotaion.AutoInject;
import com.eouna.configtool.core.boot.ICommandLineRunner;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.boot.env.CommandLineAndArgs;
import com.eouna.configtool.core.boot.env.IApplicationEnvironment;
import com.eouna.configtool.core.boot.env.NoneGuiApplicationEnvironment;
import com.eouna.configtool.core.context.ApplicationContextAware;
import com.eouna.configtool.core.context.ApplicationListener;
import com.eouna.configtool.core.event.FxApplicationStartedEvent;
import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.ui.controllers.ExcelGenWindowController;
import com.eouna.configtool.utils.FileUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * @author : [程春林(chengchunlin)]
 * @version : [v1.0]
 * @className : [ConfigToolAppEntrance]
 * @description : 描述
 * @createTime : [2026/10/9 11:19]
 */
@Component
public class ConfigToolAppEntrance
    implements ApplicationListener<FxApplicationStartedEvent>,
        ICommandLineRunner,
        ApplicationContextAware {

  private ApplicationContext applicationContext;
  @AutoInject private SystemConfigHolder systemConfigHolder;

  /** 非GUI(命令行)模式下的生成逻辑 */
  @AutoInject private NonGuiApplicationRunner nonGuiApplicationRunner;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public void run(Application.Parameters parameters) {
    Stage stage = applicationContext.getMainStage();
    // 初始化日志系统
    LoggerUtils.getInstance().init();
    LoggerUtils.getLogger().info("初始化日志系统成功");
    // 加载系统配置文件
    try {
      systemConfigHolder.loadSystemConfig();
      LoggerUtils.getLogger().info("加载系统配置文件完成");
    } catch (IOException
        | IllegalAccessException
        | InstantiationException
        | InvocationTargetException e) {
      LoggerUtils.getLogger().error("系统配置加载失败，请检查系统配置是否正确", e);
      throw new RuntimeException("系统配置加载失败，请检查系统配置是否正确", e);
    }
    // 禁止窗口重新设置大小
    stage.setResizable(false);
    stage.setMaximized(false);
    // 从pom文件中读取版本信息
    stage.setTitle("压测工具" + FileUtils.getAppVersion());
    // 初始化逻辑
    applicationContext.getBean(ConfigToolAppEntrance.class).init(applicationContext);
  }

  /** 逻辑处理的入口 */
  public void init(ApplicationContext context) {
    IApplicationEnvironment applicationEnvironment = context.getEnvironment();
    if (applicationEnvironment == null) {
      return;
    }
    // 1. 解析传入参数
    CommandLineAndArgs commandLineAndArgs = applicationEnvironment.getCommandLineAndArgs();
    if (applicationEnvironment instanceof NoneGuiApplicationEnvironment) {
      // 以非GUI方式进行: 交由 NonGuiApplicationRunner 独立线程处理, 完成后退出进程
      runNonGuiMode(commandLineAndArgs);
    } else {
      // 打开主界面
      WindowManager.getInstance().openWindow(ExcelGenWindowController.class, commandLineAndArgs);
      // 标记主窗口：关闭主窗口时退出整个程序
      WindowManager.getInstance().setMainWindow(ExcelGenWindowController.class);
    }
  }

  /** 非GUI模式运行: 生成逻辑在独立线程执行, 避免阻塞JavaFX线程; 结束后退出进程并返回退出码 */
  private void runNonGuiMode(CommandLineAndArgs commandLineAndArgs) {
    Thread nonGuiRunnerThread =
        new Thread(
            () -> {
              int exitCode = 1;
              try {
                exitCode = nonGuiApplicationRunner.run(commandLineAndArgs);
              } catch (Throwable throwable) {
                LoggerUtils.getLogger().error("非GUI模式运行异常", throwable);
              } finally {
                // 交回JavaFX线程关闭, 避免在start()尚未结束时直接退出导致的异常
                int code = exitCode;
                Platform.runLater(
                    () -> {
                      Platform.exit();
                      System.exit(code);
                    });
              }
            },
            "configtool-non-gui-runner");
    nonGuiRunnerThread.setDaemon(false);
    nonGuiRunnerThread.start();
  }

  @Override
  public void onEventHappen(FxApplicationStartedEvent event) {}
}
