package com.eouna.configtool.boot;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.event.FxApplicationStartedEvent;
import com.eouna.configtool.core.context.ApplicationListener;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.ui.controllers.ExcelGenWindowController;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.LoggerUtils;
import com.eouna.configtool.utils.NodeUtils;
import javafx.stage.Stage;

/**
 * 程序启动器 处理容器之上的初始化逻辑
 *
 * @author CCL
 * @date 2023/7/6
 */
public class ConfigToolApplicationBoot implements ApplicationListener<FxApplicationStartedEvent> {

  @Override
  public void onEventHappen(FxApplicationStartedEvent event) {
    ApplicationContext applicationContext = event.getApplicationContext();
    Stage stage = applicationContext.getMainStage();
    // 初始化日志系统
    LoggerUtils.getInstance().init();
    LoggerUtils.getLogger().info("初始化日志系统成功");

    // 加载系统配置文件
    try {
      SystemConfigHolder.getInstance().loadSystemConfig();
      LoggerUtils.getLogger().info("加载系统配置文件完成");
    } catch (IOException
        | IllegalAccessException
        | InstantiationException
        | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    // 禁止窗口重新设置大小
    stage.setResizable(false);
    stage.setMaximized(false);
    // 从pom文件中读取版本信息
    stage.setTitle("配置表工具" + FileUtils.getAppVersion());
    // 加载主场景
    WindowManager.getInstance().openWindowWithStage(stage, ExcelGenWindowController.class);
    // 加载主场景结束日志
    LoggerUtils.getTextareaLogger().info("加载主场景结束,程序PID: " + FileUtils.getPid());
    // 监听主窗口关闭事件 主窗口关闭则关闭所有窗口
    stage.setOnCloseRequest((e) -> WindowManager.getInstance().destroyAllWindow());
  }
}
