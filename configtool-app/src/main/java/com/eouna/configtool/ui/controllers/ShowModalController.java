package com.eouna.configtool.ui.controllers;

import java.io.IOException;

import com.eouna.configtool.core.window.BaseMultiWindowController;
import com.eouna.configtool.core.window.WindowManager;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * 展示模态框
 *
 * @author CCL
 */
public class ShowModalController extends BaseMultiWindowController {

  @FXML protected TextFlow errorShowArea;
  @FXML protected ScrollPane scrollPane;
  @FXML protected StackPane stackPane;

  private Stage stage;

  @Override
  public Stage initControllerStage(Parent parent, Object... initArgs) throws IOException {
    Stage newStage = super.initControllerStage(parent, initArgs);
    String title = (String) initArgs[0];
    newStage.setTitle(title);
    newStage.setResizable(false);
    newStage.initModality(Modality.NONE);
    return newStage;
  }

  public void appendText(String errMsg) {
    Text text = new Text(errMsg);
    text.setFont(new Font(14));
    text.setFontSmoothingType(FontSmoothingType.GRAY);
    text.setBoundsType(TextBoundsType.LOGICAL);
    text.getStyleClass().add("error-text");
    errorShowArea.getChildren().add(text);
    scrollPane.setVvalue(1D);
  }

  @Override
  public String getFxmlPath() {
    return "show-modal";
  }

  @Override
  public String getStageIconPath() {
    return "icon/error.png";
  }

  @Override
  public Stage getStage() {
    return stage;
  }

  @Override
  public void setStage(Stage stage) {
    this.stage = stage;
  }

  public TextFlow getErrorShowArea() {
    return errorShowArea;
  }

  public ScrollPane getScrollPane() {
    return scrollPane;
  }

  @FXML
  protected void closeWindow() {
    WindowManager.getInstance().closeWindow(getWindowId());
  }
}
