package com.eouna.configtool.ui.controllers;

import java.io.IOException;

import com.eouna.configtool.core.window.BaseMultiWindowController;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.utils.FileUtils;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
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

  @FXML protected AnchorPane backBlurPane;

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
    text.setFill(new Color(.85, .15, .15, 0.8));

    errorShowArea.getChildren().add(text);
    scrollPane.setVvalue(1D);
  }

  @Override
  public void onMounted(Object... args) {
    super.onMounted(args);
    backBlurPane.setBackground(
        new Background(
            new BackgroundImage(
                new Image(FileUtils.getFullResourceUrl("img/beach.jpg")),
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.REPEAT,
                BackgroundPosition.CENTER,
                BackgroundSize.DEFAULT)));
    BoxBlur boxBlur = new BoxBlur(20, 20, 15);
    backBlurPane.setEffect(boxBlur);
    errorShowArea
        .heightProperty()
        .addListener((observable, oldV, newV) -> backBlurPane.setPrefHeight(newV.doubleValue()));
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
