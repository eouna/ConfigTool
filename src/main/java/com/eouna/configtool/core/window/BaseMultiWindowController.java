package com.eouna.configtool.core.window;

import org.apache.commons.lang3.StringUtils;

/**
 * 多开窗口控制器
 *
 * @author CCL
 */
public abstract class BaseMultiWindowController extends BaseWindowController
    implements IMultiWindow {

  protected String windId;

  @Override
  public String generateWindowIdentifier() {
    if (StringUtils.isEmpty(windId)) {
      windId = System.currentTimeMillis() + "";
    }
    return windId;
  }

  @Override
  public String getWindowId() {
    return super.getWindowId() + "$" + generateWindowIdentifier();
  }
}
