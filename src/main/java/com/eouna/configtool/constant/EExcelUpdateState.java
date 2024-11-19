package com.eouna.configtool.constant;
/**
 * excel更新状态机
 *
 * @author CCL
 * @date 2023/4/12
 */
public enum EExcelUpdateState {
  // 默认状态
  NONE(
      0,
      new ICanGoNext() {
        @Override
        public boolean canGoNext(EExcelUpdateState excelUpdateState) {
          return excelUpdateState.priority < values().length;
        }

        @Override
        public boolean checkOpen(EExcelUpdateState excelUpdateState) {
          return excelUpdateState.priority <= 1;
        }
      }),
  // 生成模板
  GEN_TEMPLATE(
      1,
      new ICanGoNext() {
        @Override
        public boolean canGoNext(EExcelUpdateState newState) {
          return newState.priority <= 2;
        }

        @Override
        public boolean checkOpen(EExcelUpdateState excelUpdateState) {
          return excelUpdateState.priority <= 2;
        }
      }),
  // 加载数据
  LOAD_DATA(
      2,
      new ICanGoNext() {
        @Override
        public boolean canGoNext(EExcelUpdateState newState) {
          return newState.priority <= 3;
        }

        @Override
        public boolean checkOpen(EExcelUpdateState excelUpdateState) {
          return excelUpdateState.priority <= 3;
        }
      }),
  // 同步数据
  SYNC_DATA(
      3,
      new ICanGoNext() {
        @Override
        public boolean canGoNext(EExcelUpdateState newState) {
          return newState.priority == LOAD_DATA.priority
              || newState.priority == 3
              || newState == NONE;
        }

        @Override
        public boolean checkOpen(EExcelUpdateState excelUpdateState) {
          return true;
        }
      }),
  ;

  public interface ICanGoNext {

    /**
     * 是否可以从当前状态跳到下一个状态
     *
     * @param excelUpdateState excel更新状态
     * @return 是否可以跳到下一个
     */
    boolean canGoNext(EExcelUpdateState excelUpdateState);

    /**
     * 是否开启下一个状态
     *
     * @param excelUpdateState excel新状态
     * @return 开启下一个状态
     */
    boolean checkOpen(EExcelUpdateState excelUpdateState);
  }

  private final ICanGoNext canGoNext;

  private final int priority;

  EExcelUpdateState(int priority, ICanGoNext canGoNext) {
    this.canGoNext = canGoNext;
    this.priority = priority;
  }

  public boolean isCanGoNext(EExcelUpdateState excelUpdateState) {
    return canGoNext.canGoNext(excelUpdateState);
  }

  public boolean checkDisable(EExcelUpdateState excelUpdateState) {
    return !canGoNext.checkOpen(excelUpdateState);
  }
}
