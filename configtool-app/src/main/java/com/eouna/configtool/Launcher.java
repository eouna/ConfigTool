package com.eouna.configtool;

import com.eouna.configtool.boot.AspectjWeaverBootstrap;

/**
 * 单文件可执行包的启动类。
 *
 * <p>刻意不继承 {@link javafx.application.Application}: 打成自带运行时的独立程序时, 以 Application
 * 子类作为主类会触发 JavaFX 的 "JavaFX runtime components are missing" 检查; 用普通类转发即可绕开。
 *
 * @author CCL
 */
public class Launcher {

  public static void main(String[] args) throws Exception {
    // IDEA(纯javac编译)运行时不会进行AspectJ织入, 这里自动把织入器挂到当前JVM, 保证配置回存切面生效
    AspectjWeaverBootstrap.ensureAspectjWeaverLoaded();
    ConfigToolGenApplication.main(args);
  }
}