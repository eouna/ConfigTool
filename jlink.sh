if [ -d ./jre ]; then
  echo "删除旧运行库"
  rm -rf ./jre
fi
jlink --module-path "%JAVA_HOME%/jmods" --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.rmi,java.scripting,java.security.jgss,java.sql,java.xml,java.xml.crypto,javafx.media,javafx.base,javafx.graphics,javafx.fxml,javafx.controls,jdk.jfr,jdk.unsupported --compress=2 --no-header-files --no-man-pages --output ../jre
echo "构建精简运行库成功"
sleep 2
exit 0
