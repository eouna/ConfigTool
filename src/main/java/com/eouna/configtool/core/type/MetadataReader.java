package com.eouna.configtool.core.type;

import java.io.File;

/**
 * 元数据读取接口
 *
 * @author CCL
 * @date 2023/9/28
 */
public interface MetadataReader {

  /**
   * 数据源
   *
   * @return 数据源
   */
  File getSource();

  /**
   * 获取类元数据
   *
   * @return 类元数据
   */
  ClassMetadata getClassMetadata();

  /**
   * 注解类元数据
   *
   * @return 类元数据
   */
  AnnotationMetadata getAnnotationMetadata();
}
