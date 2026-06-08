/**
 * 核心模块:通用容器/窗口/日志框架,不依赖任何具体业务代码。
 */
module com.eouna.core {
    requires java.compiler;
    requires java.management;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    requires org.controlsfx.controls;
    requires com.google.common;
    requires org.apache.commons.lang3;
    requires org.slf4j;
    requires static lombok;

    exports com.eouna.configtool.core;
    exports com.eouna.configtool.core.annotaion;
    exports com.eouna.configtool.core.boot;
    exports com.eouna.configtool.core.boot.configure;
    exports com.eouna.configtool.core.boot.context;
    exports com.eouna.configtool.core.boot.context.event;
    exports com.eouna.configtool.core.boot.convert;
    exports com.eouna.configtool.core.boot.env;
    exports com.eouna.configtool.core.context;
    exports com.eouna.configtool.core.context.event;
    exports com.eouna.configtool.core.context.support;
    exports com.eouna.configtool.core.event;
    exports com.eouna.configtool.core.exceptions;
    exports com.eouna.configtool.core.factory;
    exports com.eouna.configtool.core.factory.anno;
    exports com.eouna.configtool.core.factory.bean;
    exports com.eouna.configtool.core.factory.config;
    exports com.eouna.configtool.core.factory.exceptions;
    exports com.eouna.configtool.core.factory.support;
    exports com.eouna.configtool.core.io;
    exports com.eouna.configtool.core.logger;
    exports com.eouna.configtool.core.type;
    exports com.eouna.configtool.core.utils;
    exports com.eouna.configtool.core.watcher;
    exports com.eouna.configtool.core.window;
}