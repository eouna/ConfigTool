@echo off
if exist "%~dp0\jre" (
  rmdir /s /q "%~dp0\jre"
  echo delete old jre success
)
jlink.exe --module-path "%JAVA_HOME%/jmods" --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.rmi,java.scripting,java.security.jgss,java.sql,java.xml,java.xml.crypto,javafx.media,javafx.base,javafx.graphics,javafx.fxml,javafx.controls,jdk.jfr,jdk.unsupported --output jre
echo build simple runtime env success