package com.eouna.configtool;

import com.eouna.configtool.boot.ConfigToolApplicationBoot;
import com.eouna.configtool.core.FxApplicationLoader;
import com.eouna.configtool.core.annotaion.FxApplication;
import com.eouna.configtool.core.event.FxApplicationStartedEvent;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.generator.ExcelTemplateGenUtils;
import com.eouna.configtool.utils.FileUtils;
import com.eouna.configtool.utils.LoggerUtils;
import com.eouna.configtool.utils.NodeUtils;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * 配置表生成工具,具体初始化逻辑{@link ConfigToolApplicationBoot#onEventHappen(FxApplicationStartedEvent)}
 *
 * @author CCL
 */
@FxApplication(componentScanPath = {"com.eouna"})
public class ConfigToolGenApplication extends Application {

  @Override
  public void start(Stage stage) {
    // 初始化全局stage
    FxApplicationLoader.run(ConfigToolGenApplication.class, stage, getParameters());
    // 设置主机服务
    NodeUtils.hostServices = ConfigToolGenApplication.this.getHostServices();
    // 添加关闭监听事件
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook-thread"));
  }

  @Override
  public void stop() throws Exception {
    super.stop();
    // 处理关闭逻辑
    shutdown();
  }

  private void shutdown() {
    LoggerUtils.getLogger().info("程序收到关闭指令,开始关闭...");
    try {
      // excel处理线程关闭
      ExcelTemplateGenUtils.onShutdown();
    } catch (Exception e) {
      LoggerUtils.getLogger().info("关闭异常");
    } finally {
      // 关闭所有窗口
      WindowManager.getInstance().destroyAllWindow();
      LoggerUtils.getLogger().info("程序收到关闭指令,关闭完成");
    }
  }

  public static void main(String[] args) throws Exception {
    FileUtils.genServerPid();
    ConfigToolGenApplication.launch(args);
  }
}
