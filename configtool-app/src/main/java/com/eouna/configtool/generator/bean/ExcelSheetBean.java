package com.eouna.configtool.generator.bean;

import java.io.File;

import com.eouna.configtool.generator.exceptions.ExcelParseException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * excel sheet gentemppath.bean
 *
 * @author CCL
 * @date 2023/3/3
 */
public class ExcelSheetBean {

  private final String sheetName;

  private final File file;

  private int rowStartNum;

  private int rowEndNum;

  private int colStartNum;

  private int colEndNum;

  public ExcelSheetBean(File file, Sheet sheet) {
    this.sheetName = sheet.getSheetName();
    this.rowStartNum = sheet.getFirstRowNum();
    this.rowEndNum = sheet.getLastRowNum();
    this.file = file;
    Row row;
    if ((row = sheet.getRow(0)) != null) {
      this.colStartNum = row.getFirstCellNum();
      this.colEndNum = row.getLastCellNum();
    } else {
      throw new ExcelParseException("sheet: " + sheetName + " is empty data");
    }
  }

  public ExcelSheetBean(File file, String sheetName) {
    this.sheetName = sheetName;
    this.file = file;
  }

  public String getSheetName() {
    return sheetName;
  }

  public int getRowStartNum() {
    return rowStartNum;
  }

  public int getRowEndNum() {
    return rowEndNum;
  }

  public int getColStartNum() {
    return colStartNum;
  }

  public int getColEndNum() {
    return colEndNum;
  }

  public File getFile() {
    return file;
  }
}
