package com.eouna.configtool.ui.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eouna.configtool.core.window.BaseWindowController;
import com.eouna.configtool.constant.DefaultEnvConfigConstant;
import com.eouna.configtool.utils.NodeUtils;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;

/**
 * excel格式解释控制器
 *
 * @author CCL
 */
public class ConfigExplainController extends BaseWindowController {

  @FXML TableColumn<ConfigExplain, String> fieldTypeCol;
  @FXML TableColumn<ConfigExplain, String> fieldConfigCol;

  @FXML TableColumn<ConfigExplain, String> fieldDataRange;

  @FXML TableColumn<ConfigExplain, String> fieldExplainCol;
  @FXML TableView<ConfigExplain> tableView;

  @FXML
  public static final ObservableList<ConfigExplain> CONFIG_EXPLAINS =
      FXCollections.observableArrayList(
          new ConfigExplain("单字节", "byte/Byte", "±" + Byte.MAX_VALUE, "填入字节范围内的数字"),
          new ConfigExplain("短整形", "short/Short", "±" + Short.MAX_VALUE, "填入短整形范围内的数字"),
          new ConfigExplain("整形", "int/Int, integer/Integer", "±21亿", "填入整形数字"),
          new ConfigExplain("长整形", "long/Long", "±92*10^17", "填入整形数字,向下兼容int"),
          new ConfigExplain(
              "布尔类型", "bool/Bool, boolean/Boolean", "ture/false,True/False,TRUE/FALSE", "填入布尔值"),
          new ConfigExplain("浮点类型", "float/Float", "±3.4028235E38(7-8位有效)", "填入浮点值"),
          new ConfigExplain(
              "双精度浮点", "double/Double", "±1.79E+308(16-17位有效)", "填入双精度值(一般不会用到这么精确,考虑使用float)"),
          new ConfigExplain("字符串", "string/String", "任意字符", "填入随意字符串"),
          new ConfigExplain(
              "时间格式",
              "Date/date",
              "无",
              """
                  填入时间格式参照下面格式:
                  yyyy：年
                  MM：月
                  dd：日
                  hh：1~12小时制(1-12)
                  HH：24小时制(0-23)
                  mm：分
                  ss：秒
                  S：毫秒
                  E：星期几
                  D：一年中的第几天
                  F：一月中的第几个星期(会把这个月总共过的天数除以7)
                  w：一年中的第几个星期
                  W：一月中的第几星期(会根据实际情况来算)
                  a：上下午标识
                  k：和HH差不多，表示一天24小时制(1-24)。
                  K：和hh差不多，表示一天12小时制(0-11)。
                  z：表示时区
                  配置实例:
                    配置实例1#
                      配置类型: Date<yyyy-MM-dd HH:mm:ss>
                      配置数据: 1999-12-31 23:59:59
                    配置实例1#
                      配置类型: Date<yyyy-MM-dd'T'HH:mm:ss.SSSX>
                      配置数据: 2022-07-09T07:18:00.144Z
                  更多实例参见链接: ${dateFormatLink}.
                  """,
              Map.of(
                  "dateFormatLink",
                  () ->
                      NodeUtils.createJumpLink(
                          "https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html"))),
          new ConfigExplain(
              "枚举",
              "枚举名(枚举1,枚举2)",
              "枚举1,枚举2",
              "填入枚举范围内的字符串,不区分大小写.在列表和键值对中使用时,需要以\"枚举名()\"的方式配置,例如:\n"
                  + "配置实例1#\n  配置类型: ActivityType(Reward,OnTime,TimeRange)\n  配置数据: Reward\n"
                  + "配置实例2(假如已有枚举ActivityType(Time,Date))#\n  配置类型: Map<ActivityType(){,}int>{;}\n  配置数据: Time,1;Date,2\n"
                  + "配置实例3(假如已有枚举ActivityType(Time,Date))#\n  配置类型: List<ActivityType()>{,}\n  配置数据: Time,Date\n\n"),
          new ConfigExplain(
              "分表结构",
              "父表 " + DefaultEnvConfigConstant.EXCEL_STRUCTURE_DELIMITER + " 子表",
              "无",
              "类型说明: 用大型表进行分割成多个子表或者单表需要按照某种类型进行区分,当前仅支持一层结构即'父表"
                  + DefaultEnvConfigConstant.EXCEL_STRUCTURE_DELIMITER
                  + "子表',使用'"
                  + DefaultEnvConfigConstant.EXCEL_STRUCTURE_DELIMITER
                  + "'进行分隔"),
          new ConfigExplain(
              "列表类型",
              "List<其他类型>{列表内元素的分隔符}",
              "无",
              "类型说明: 用于多个单一类型的元素组成的列表\n配置规则: 列表内的类型支持所有表内的类型,并且支持嵌套结构,有嵌套结构时分隔符必须保持唯一\n"
                  + "配置实例1#\n  配置类型: List<int>{;}\n  配置数据: 1;2;3;4\n"
                  + "配置实例2#\n  配置类型: List<int>{;5}\n  配置数据: 1;2;3;4;5 表示只能添加5个整形元素,超过则会抛出异常\n"
                  + "配置实例3#\n  配置类型: List<List<int>{,}>{;}\n  配置数据: 1,2,3,4;2,3,4;3,4,5;4,5,6 表示四组数据每组数据有N个元素\n"
                  + "配置实例4#\n  配置类型: List<Map<int{+}String>{,}>{;}\n  配置数据: 1+a,2+b,3+c,4+d;2+b,3+c,4+d 表示两组键值数据 每组数据有键为int值为String的数据\n"),
          new ConfigExplain(
              "Set类型", "Set<其他类型>{列表内元素的分隔符}", "无", "类型说明: 用于多个单一类型元素且不重复的数据组成的列表\n配置规则: 同列表类型\n"),
          new ConfigExplain(
              "键值类型",
              "Map<键类型{键和值的分隔符}值类型>{多个键值对的分隔符}",
              "无",
              "类型说明: 用于多个键值对元组组成的配置表字段\n配置规则: 键值对内的键类型支持整形、长整形、浮点类型、双精度和字符串\n"
                  + "键配置时需注意: 键不支持布尔类型和列表类型\n"
                  + "键值对内的值的类型支持所有类型,支持嵌套.\n"
                  + "键值对配置时需注意所有分隔符只能出现一次\n"
                  + "配置实例1#\n  配置类型: Map<String{,}Int>{;}\n  配置数据: count,1;num,2;itemCount,1;\n"
                  + "配置实例2#\n  配置类型: Map<String{,}Int>{;2}\n  配置数据: count,1;num,2;itemCount,1;\n 表示只能存在两组键值对,超出则抛出异常\n"
                  + "配置实例3#\n  配置类型: Map<Int{,}List<String>{+}>{;}\n  配置数据: 1,this+is;2,int+list+str;3,map+type\n"
                  + "配置实例4#\n  配置类型: Map<Int{,}Map<String{-}Float>{+}>{;}\n  配置数据: 1,this-1.0+is-2.0;2,int-4.0+map-5.0+str-6.0;3,float-7.0+map-8.0\n"
                  + "配置实例5#\n  配置类型: Map<String{#}Map<String{-}List<Integer>{?}>{+}>{;}\n  配置数据: id#DD-12?12?12+AA-13?13?13;id2#EE-22?22+FF-23?23?23\n"));

  @Override
  public void onCreate(Stage stage) {
    stage.setResizable(false);
  }

  @Override
  public String getTitle() {
    return "配置表字段配置说明";
  }

  @Override
  public void onMounted(Object... args) {
    super.onMounted(args);
    fieldTypeCol.setCellValueFactory(new PropertyValueFactory<>("fieldType"));
    fieldConfigCol.setCellValueFactory(new PropertyValueFactory<>("acceptedChar"));
    fieldDataRange.setCellValueFactory(new PropertyValueFactory<>("fieldDataRange"));
    fieldExplainCol.setCellValueFactory(new PropertyValueFactory<>("configExplain"));
    tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    tableView.setRowFactory(
        tv -> {
          TableRow<ConfigExplain> tableRow = new TableRow<>();
          tableRow
              .selectedProperty()
              .addListener(
                  (observable, oldValue, newValue) -> {
                    if (newValue) {
                      tableRow.setStyle("-fx-background-color: #ccc; -fx-opacity: 0.6");
                    } else {
                      tableRow.setStyle("-fx-background-color: white");
                    }
                  });
          return tableRow;
        });
    tableView.setItems(CONFIG_EXPLAINS);
  }

  @Override
  public String getFxmlPath() {
    return "config-explain";
  }

  @Override
  public String getStageIconPath() {
    return "icon/book.png";
  }

  protected static class ConfigExplain {
    private final Node fieldType;
    private final Node acceptedChar;
    private final Node configExplain;

    private final Node fieldDataRange;

    private final Pattern paramFlagPattern = Pattern.compile("\\$\\{(.*?)\\}");

    public ConfigExplain(
        String fieldType, String acceptedChar, String fieldDataRange, String configExplain) {
      TextFlow textFlow = new TextFlow();
      textFlow.setTextAlignment(TextAlignment.LEFT);
      String[] configExplainStrArr = configExplain.split("\n");
      for (String line : configExplainStrArr) {
        textFlow.getChildren().add(new Text(line + "\n"));
      }
      textFlow.setPrefHeight(5 * configExplainStrArr.length);
      this.configExplain = textFlow;
      this.fieldType = new Text(fieldType);
      this.fieldDataRange = new Text(fieldDataRange);
      this.acceptedChar = new Text(acceptedChar);
    }

    public ConfigExplain(
        String fieldType,
        String acceptedChar,
        String fieldDataRange,
        String configExplain,
        Map<String, Supplier<Node>> configExplainReplaceMap) {
      this.fieldType = new Text(fieldType);
      this.fieldDataRange = new Text(fieldDataRange);
      this.acceptedChar = new Text(acceptedChar);
      TextFlow textFlow = new TextFlow();
      textFlow.setTextAlignment(TextAlignment.LEFT);
      String[] configExplainStrArr = configExplain.split("\n");
      if (configExplainReplaceMap != null) {
        parseNode(configExplainStrArr, configExplainReplaceMap, textFlow);
      } else {
        for (String line : configExplainStrArr) {
          if (StringUtils.isEmpty(line)) {
            continue;
          }
          textFlow.getChildren().add(new Text(line + "\n"));
        }
      }
      textFlow.setPrefHeight(5 * configExplainStrArr.length);
      this.configExplain = textFlow;
    }

    private void parseNode(
        String[] configExplainStrArr,
        Map<String, Supplier<Node>> configExplainReplaceMap,
        TextFlow textFlow) {
      for (String line : configExplainStrArr) {
        if (StringUtils.isEmpty(line)) {
          continue;
        }
        Matcher matcher = paramFlagPattern.matcher(line);
        if (matcher.find()) {
          List<Node> lineNode = new ArrayList<>();
          matcher.reset();
          int parseIdx = 0;
          while (matcher.find()) {
            String flagStr = matcher.group(1);
            if (StringUtils.isEmpty(flagStr)) {
              continue;
            }
            if (configExplainReplaceMap.containsKey(flagStr)) {
              String fullFlag = "${" + flagStr + "}";
              int indexOfFullFlag = line.indexOf(fullFlag);
              lineNode.add(new Text(line.substring(parseIdx, indexOfFullFlag)));
              lineNode.add(configExplainReplaceMap.get(flagStr).get());
              parseIdx = indexOfFullFlag + fullFlag.length();
            }
          }
          if (parseIdx != line.length()) {
            lineNode.add(new Text(line.substring(parseIdx)));
          }
          lineNode.add(new Text("\n"));
          textFlow.getChildren().addAll(lineNode);
        } else {
          textFlow.getChildren().add(new Text(line + "\n"));
        }
      }
    }

    public Node getFieldType() {
      return fieldType;
    }

    public Node getAcceptedChar() {
      return acceptedChar;
    }

    public Node getConfigExplain() {
      return configExplain;
    }

    public Node getFieldDataRange() {
      return fieldDataRange;
    }
  }
}
