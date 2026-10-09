/**
 * 配置表工具功能模块:界面、配置表解析/生成等具体业务实现。
 *
 * <p>使用 open module, 便于 FXML 控制器反射注入、以及核心模块扫描并实例化业务 bean。
 */
open module com.eouna.configtool {
  requires com.eouna.core;
  requires javafx.controls;
  requires javafx.fxml;
  requires javafx.graphics;
  requires org.controlsfx.controls;
  requires org.kordamp.bootstrapfx.core;
  requires org.apache.commons.codec;
  requires org.apache.poi.poi;
  requires org.apache.poi.ooxml;
  requires org.apache.poi.ooxml.schemas;
  requires org.apache.commons.lang3;
  requires org.yaml.snakeyaml;
  requires com.google.common;
  requires freemarker;
  requires org.apache.commons.io;
  requires com.google.gson;
  requires java.compiler;
  requires java.management;
  requires org.kordamp.ikonli.core;
  requires org.kordamp.ikonli.fontawesome;
  requires org.kordamp.ikonli.javafx;
  requires org.slf4j;
  requires jsch;
  requires org.aspectj.runtime;
  requires maven.model;
  requires plexus.utils;
  requires java.desktop;
  requires org.eclipse.jdt.core.compiler.batch;
  requires static lombok;

  exports com.eouna.configtool;
}
