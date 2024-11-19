package com.eouna.configtool.utils.caller;


import com.eouna.configtool.core.watcher.TimeConsumeWatcher;

/**
 * @author KOUJIANG
 * @date Created in 2023/3/14
 */
public abstract class AbsTimeInferFaceCallImpl<T> implements IInterfaceCallEachAction<T> {
  TimeConsumeWatcher timeConsumeWatcher;

  public AbsTimeInferFaceCallImpl() {
  }

  public void setTimeConsumeWatcher(TimeConsumeWatcher timeConsumeWatcher) {
    this.timeConsumeWatcher = timeConsumeWatcher;
  }

  @Override
  public void callBefore(T classEntity) {
    timeConsumeWatcher.start("class#"
            + classEntity.getClass().getSimpleName());
    IInterfaceCallEachAction.super.callBefore(classEntity);
  }

  @Override
  public void afterCall(T classEntity) {
    timeConsumeWatcher.stop();
  }
}
