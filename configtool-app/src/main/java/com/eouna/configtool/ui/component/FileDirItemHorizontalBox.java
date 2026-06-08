package com.eouna.configtool.ui.component;

import com.google.common.base.Objects;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.layout.HBox;

/**
 * 水平文件目录item
 *
 * @author CCL
 * @date 2023/8/2
 */
public class FileDirItemHorizontalBox extends HBox {
  /** 文件相对于root的路径深度 */
  public int dirDepth;
  /** 当前行保存的行文件对象 */
  public File file;
  /** 父文件节点 */
  public FileDirItemHorizontalBox parentFile;
  /** */
  public final List<FileDirItemHorizontalBox> childList = new ArrayList<>();
  /** 当前的点击状态 */
  public ClickStatus clickStatus =
      ClickStatus.NONE;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FileDirItemHorizontalBox)) {
      return false;
    }
    FileDirItemHorizontalBox dirItem = (FileDirItemHorizontalBox) o;
    return file.getAbsolutePath().equals(dirItem.file.getAbsolutePath());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(dirDepth, file, clickStatus);
  }

  public enum ClickStatus {
    // 初始状态
    NONE("#FFF"),
    // 准备状态
    READ("#FFF"),
    // 已选择但当前不处于目录选择
    SELECTED_WAIT("#3CED0DFF"),
    // 选择就绪
    SELECTED_DONE("#27D878FF"),
    ;
    public final String backgroundColor;

    ClickStatus(String backgroundColor) {
      this.backgroundColor = backgroundColor;
    }
  }
}
