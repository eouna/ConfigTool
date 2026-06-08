package com.eouna.configtool.generator.base;

import java.io.File;
import java.util.Set;

/**
 * 双元组
 *
 * @author CCL
 * @date 2023/3/9
 */
public class ExcelFileStructure {

  /** 子节点 */
  private Set<File> childFileSet;

  /** 当前excel */
  private File current;

  /** 父节点 */
  private File parentFile;

  public ExcelFileStructure(File current, File parentFile, Set<File> childFileSet) {
    this.childFileSet = childFileSet;
    this.current = current;
    this.parentFile = parentFile;
  }

  public Set<File> getChildFileSet() {
    return childFileSet;
  }

  public void setChildFileSet(Set<File> childFileSet) {
    this.childFileSet = childFileSet;
  }

  public File getParentFile() {
    return parentFile;
  }

  public void setParentFile(File parentFile) {
    this.parentFile = parentFile;
  }

  public File getCurrent() {
    return current;
  }

  public void setCurrent(File current) {
    this.current = current;
  }
}
