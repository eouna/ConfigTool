package com.eouna.configtool.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 压缩文件工具类
 *
 * @author CCL
 * @date 2023/8/1
 */
public class ZipUtils {

  /**
   * 通过Map压缩文件
   *
   * @param excelFileList 压缩文件夹名 <=> 对应文件夹下的文件列表
   * @param outFilePathAndName 输出文件全路径名
   * @return 压缩文件对象
   */
  public static File zipFileByFileNameMap(
      Map<String, List<File>> excelFileList, String outFilePathAndName) {
    ZipOutputStream zipOutputStream = null;
    // 对文件夹进行压缩
    try {
      zipOutputStream = new ZipOutputStream(new FileOutputStream(outFilePathAndName));
      for (Entry<String, List<File>> fileEntry : excelFileList.entrySet()) {
        // excel文件夹名
        String excelDir = fileEntry.getKey();
        for (File file : fileEntry.getValue()) {
          // 使用构造后的文件夹路径
          String zipFileName = excelDir + "/" + file.getName();
          addFileToZip(file, zipFileName, zipOutputStream);
        }
      }
      zipOutputStream.flush();
      zipOutputStream.finish();
      zipOutputStream.close();
      return new File(outFilePathAndName);
    } catch (FileNotFoundException e) {
      LoggerUtils.showErrorDialog("未找到压缩文件", e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (zipOutputStream != null) {
        try {
          zipOutputStream.close();
        } catch (IOException e) {
          LoggerUtils.showErrorDialog("压缩文件时关闭输出流失败", e);
        }
      }
    }
  }

  /**
   * 压缩文件夹
   *
   * @param dirList 文件夹
   * @param filePath 压缩文件输出路径
   * @param isKeepSubDirStruct 是否保持目录结构
   * @param isContainParentDir 是否包含选择的目录，如果包含则压缩包中第一级则选中的文件夹
   * @return 压缩文件
   */
  public static File compressDirList(
      Collection<File> dirList,
      String filePath,
      boolean isKeepSubDirStruct,
      boolean isContainParentDir,
      ZipEntryFileChecker checker) {
    ZipOutputStream zipOutputStream;
    try {
      zipOutputStream = new ZipOutputStream(new FileOutputStream(filePath), StandardCharsets.UTF_8);
      for (File file : dirList) {
        if (checker != null && checker.errorFileFilter.accept(file)) {
          checker.getErrorFileList().add(file);
        }
        String path = file.getAbsolutePath();
        // 压缩文件夹
        compressDir(file, path, isKeepSubDirStruct, isContainParentDir, zipOutputStream, checker);
      }
      if (checker != null && !checker.getErrorFileList().isEmpty()) {
        zipOutputStream.finish();
        zipOutputStream.close();
        return null;
      } else {
        zipOutputStream.flush();
        zipOutputStream.finish();
        zipOutputStream.close();
        return new File(filePath);
      }
    } catch (FileNotFoundException e) {
      LoggerUtils.showErrorDialog("文件找不到: " + filePath, e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      LoggerUtils.showErrorDialog("压缩异常: " + e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  /**
   * 压缩文件夹
   *
   * @param zipFile 文件夹
   * @param isKeepSubDirStruct 是否保持目录结构
   * @param zipOutputStream 压缩文件输出流
   * @throws IOException io异常
   */
  public static void compressDir(
      File zipFile,
      boolean isKeepSubDirStruct,
      boolean isContainParentDir,
      ZipOutputStream zipOutputStream)
      throws IOException {
    compressDir(
        zipFile,
        zipFile.getAbsolutePath(),
        isKeepSubDirStruct,
        isContainParentDir,
        zipOutputStream,
        null);
  }

  /**
   * 压缩文件夹
   *
   * @param zipFile 文件夹
   * @param baseFilePath 原始文件夹
   * @param isKeepSubDirStruct 是否保持目录结构
   * @param zipOutputStream 压缩文件输出流
   * @throws IOException io异常
   */
  private static void compressDir(
      File zipFile,
      String baseFilePath,
      boolean isKeepSubDirStruct,
      boolean isContainParentDir,
      ZipOutputStream zipOutputStream,
      ZipEntryFileChecker checker)
      throws IOException {
    if (!zipFile.exists()) {
      return;
    }
    if (zipFile.isDirectory()) {
      File[] files = zipFile.listFiles();
      if (files == null) {
        return;
      }
      for (File file : files) {
        if (checker != null && checker.errorFileFilter.accept(file)) {
          checker.getErrorFileList().add(file);
        }
        compressDir(
            file, baseFilePath, isKeepSubDirStruct, isContainParentDir, zipOutputStream, checker);
      }
    } else {
      String zipFileName = "";
      if (isKeepSubDirStruct) {
        int baseFilePathIdx = zipFile.getAbsolutePath().indexOf(baseFilePath);
        if (baseFilePathIdx >= 0) {
          zipFileName = zipFile.getAbsolutePath().substring(baseFilePath.length() + 1);
        }
        zipFileName = zipFileName.replace("\\", "/");
        if (isContainParentDir) {
          String baseFileName =
              baseFilePath.substring(baseFilePath.lastIndexOf(File.separator) + 1);
          zipFileName = baseFileName + "/" + zipFileName;
        }
      } else {
        zipFileName = zipFile.getName();
      }
      addFileToZip(zipFile, zipFileName, zipOutputStream);
    }
  }

  /**
   * 压缩单个文件
   *
   * @param file 文件
   * @param zipFileName 文件名
   * @param zipOutputStream 压缩输入流
   * @throws IOException io异常
   */
  public static void addFileToZip(File file, String zipFileName, ZipOutputStream zipOutputStream)
      throws IOException {
    // 参照基础文件目录,并保留文件结构
    ZipEntry zipEntry = new ZipEntry(zipFileName);
    zipOutputStream.putNextEntry(zipEntry);
    FileInputStream fileInputStream = new FileInputStream(file);
    byte[] bufArr = new byte[1024];
    int readLen;
    while ((readLen = fileInputStream.read(bufArr)) > -1) {
      zipOutputStream.write(bufArr, 0, readLen);
    }
    zipOutputStream.closeEntry();
    fileInputStream.close();
  }

  /** 压缩文件检查 */
  public static class ZipEntryFileChecker {
    /** 错误文件过滤器 */
    private Filter<File> errorFileFilter;
    /** 错误文件列表 */
    private final List<File> errorFileList = new ArrayList<>();

    public ZipEntryFileChecker() {}

    public ZipEntryFileChecker(Filter<File> errorFileFilter) {
      this.errorFileFilter = errorFileFilter;
    }

    public Filter<File> getErrorFileFilter() {
      return errorFileFilter;
    }

    public void setErrorFileFilter(Filter<File> errorFileFilter) {
      this.errorFileFilter = errorFileFilter;
    }

    public List<File> getErrorFileList() {
      return errorFileList;
    }
  }
}
