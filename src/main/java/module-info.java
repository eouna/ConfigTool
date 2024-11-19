module com.eouna.configtool {
  requires javafx.controls;
  requires javafx.fxml;
  requires javafx.graphics;
  requires org.controlsfx.controls;
  requires org.kordamp.bootstrapfx.core;
  requires org.apache.commons.codec;
  requires poi;
  requires poi.ooxml;
  requires poi.ooxml.schemas;
  requires org.apache.commons.lang3;
  requires org.yaml.snakeyaml;
  requires com.google.common;
  requires freemarker;
  requires org.apache.commons.io;
  requires com.google.gson;
  requires java.compiler;
  requires java.management;
  requires java.base;
  requires org.kordamp.ikonli.core;
  requires org.kordamp.ikonli.fontawesome;
  requires org.kordamp.ikonli.javafx;
  requires org.slf4j;
  requires jsch;
  requires org.aspectj.runtime;
  requires maven.model;
  requires plexus.utils;
	requires java.desktop;

	opens com.eouna.configtool.ui.controllers to
      javafx.fxml,
      javafx.base,
      javafx.controls,
      javafx.graphics;
  opens com.eouna.configtool.ui.component to
      javafx.fxml,
      javafx.base,
      javafx.controls,
      javafx.graphics;

  exports com.eouna.configtool.generator.bean to
      freemarker;
  opens com.eouna.configtool to java.base;
  exports com.eouna.configtool;
}
