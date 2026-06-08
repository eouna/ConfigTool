package com.eouna.configtool.core.window;

/**
 * 当某个视图被创建完成且完成加载后初始化
 *
 * @author CCL
 * @date 2023/3/1
 */
public interface IWindowViewRenderComplete {

  /**
   * 视图装载完成后
   *
   * @param args args
   */
  void onMounted(Object... args);
}
