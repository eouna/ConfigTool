package com.eouna.configtool.core.watcher;

import com.eouna.configtool.constant.StrConstant;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * 耗时观察 参考{@linkplain StopWatch} 对原版做了多任务的优化和打印时的条件处理
 *
 * @author CCL
 * @date 2023/2/13
 */
public class TimeConsumeWatcher {

  private final String id;

  private boolean keepTaskList = true;

  private final List<TaskInfo> taskList = new LinkedList<>();

  /** Start time of the current task. */
  private long startTimeNanos;

  /** Name of the current task. */
  private String currentTaskName;

  private TaskInfo lastTaskInfo;

  private int taskCount;

  /** Total running time. */
  private long totalTimeNanos;

  public TimeConsumeWatcher() {
    this("");
  }

  public TimeConsumeWatcher(String id) {
    this.id = id;
  }

  public String getId() {
    return this.id;
  }

  public void setKeepTaskList(boolean keepTaskList) {
    this.keepTaskList = keepTaskList;
  }

  public void start() throws IllegalStateException {
    start("");
  }

  public synchronized void start(String taskName) throws IllegalStateException {
    if (this.currentTaskName != null) {
      throw new IllegalStateException("Can't start StopWatch: it's already running");
    }
    this.currentTaskName = taskName;
    this.startTimeNanos = System.nanoTime();
  }

  public synchronized void stop() throws IllegalStateException {
    if (this.currentTaskName == null) {
      throw new IllegalStateException("Can't stop StopWatch: it's not running");
    }
    long lastTime = System.nanoTime() - this.startTimeNanos;
    this.totalTimeNanos += lastTime;
    this.lastTaskInfo = new TaskInfo(this.currentTaskName, lastTime);
    if (this.keepTaskList) {
      this.taskList.add(this.lastTaskInfo);
    }
    ++this.taskCount;
    this.currentTaskName = null;
  }

  /**
   * Determine whether this {@code StopWatch} is currently running.
   *
   * @see #currentTaskName()
   */
  public boolean isRunning() {
    return (this.currentTaskName != null);
  }

  public String currentTaskName() {
    return this.currentTaskName;
  }

  public long getLastTaskTimeNanos() throws IllegalStateException {
    if (this.lastTaskInfo == null) {
      throw new IllegalStateException("No tasks run: can't get last task interval");
    }
    return this.lastTaskInfo.getTimeNanos();
  }

  /**
   * Get the time taken by the last task in milliseconds.
   *
   * @see #getLastTaskTimeNanos()
   */
  public long getLastTaskTimeMillis() throws IllegalStateException {
    if (this.lastTaskInfo == null) {
      throw new IllegalStateException("No tasks run: can't get last task interval");
    }
    return this.lastTaskInfo.getTimeMillis();
  }

  /** Get the name of the last task. */
  public String getLastTaskName() throws IllegalStateException {
    if (this.lastTaskInfo == null) {
      throw new IllegalStateException("No tasks run: can't get last task name");
    }
    return this.lastTaskInfo.getTaskName();
  }

  /** Get the last task as a {@link TaskInfo} object. */
  public TaskInfo getLastTaskInfo() throws IllegalStateException {
    if (this.lastTaskInfo == null) {
      throw new IllegalStateException("No tasks run: can't get last task info");
    }
    return this.lastTaskInfo;
  }

  public long getTotalTimeNanos() {
    return this.totalTimeNanos;
  }

  public long getTotalTimeMillis() {
    return nanosToMillis(this.totalTimeNanos);
  }

  public double getTotalTimeSeconds() {
    return nanosToSeconds(this.totalTimeNanos);
  }

  /** Get the number of tasks timed. */
  public int getTaskCount() {
    return this.taskCount;
  }

  /** Get an array of the data for tasks performed. */
  public TaskInfo[] getTaskInfo() {
    if (!this.keepTaskList) {
      throw new UnsupportedOperationException("Task info is not being kept!");
    }
    return this.taskList.toArray(new TaskInfo[0]);
  }

  /** Get a short description of the total running time. */
  public String shortSummary() {
    return "StopWatch '"
        + getId()
        + "': running time = "
        + nanosToMillis(getTotalTimeNanos())
        + " ms";
  }

  public String prettyPrint() {
    return prettyPrint(taskInfo -> true);
  }

  /**
   * 打印数据
   *
   * @param taskInfoPredicate 打印过滤
   * @return 耗时任务数据
   */
  public String prettyPrint(Predicate<TaskInfo> taskInfoPredicate) {
    StringBuilder sb = new StringBuilder(shortSummary());
    sb.append('\n');
    if (!this.keepTaskList) {
      sb.append("No task info kept");
    } else {
      sb.append("---------------------------------------------\n");
      sb.append("ms         %     Task name\n");
      sb.append("---------------------------------------------\n");
      NumberFormat nf = NumberFormat.getNumberInstance();
      nf.setMinimumIntegerDigits(9);
      nf.setGroupingUsed(false);
      NumberFormat pf = NumberFormat.getPercentInstance();
      pf.setMinimumIntegerDigits(3);
      pf.setGroupingUsed(false);
      int taskPrintCount = 0;
      for (TaskInfo task : getTaskInfo()) {
        if (taskInfoPredicate.test(task)) {
          taskPrintCount++;
          sb.append(nf.format(nanosToMillis(task.getTimeNanos()))).append("  ");
          sb.append(pf.format((double) task.getTimeNanos() / getTotalTimeNanos())).append("  ");
          sb.append(task.getTaskName()).append("\n");
        }
      }
      return taskPrintCount > 0 ? sb.toString() : StrConstant.EMPTY;
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(shortSummary());
    if (this.keepTaskList) {
      for (TaskInfo task : getTaskInfo()) {
        sb.append("; [")
            .append(task.getTaskName())
            .append("] took ")
            .append(task.getTimeNanos())
            .append(" ns");
        long percent = Math.round(100.0 * task.getTimeNanos() / getTotalTimeNanos());
        sb.append(" = ").append(percent).append("%");
      }
    } else {
      sb.append("; no task info kept");
    }
    return sb.toString();
  }

  private static long nanosToMillis(long duration) {
    return TimeUnit.NANOSECONDS.toMillis(duration);
  }

  private static double nanosToSeconds(long duration) {
    return duration / 1_000_000_000.0;
  }

  /** Nested class to hold data about one task executed within the {@code StopWatch}. */
  public static final class TaskInfo {

    private final String taskName;

    private final long timeNanos;

    TaskInfo(String taskName, long timeNanos) {
      this.taskName = taskName;
      this.timeNanos = timeNanos;
    }

    /** Get the name of this task. */
    public String getTaskName() {
      return this.taskName;
    }

    public long getTimeNanos() {
      return this.timeNanos;
    }

    public long getTimeMillis() {
      return nanosToMillis(this.timeNanos);
    }

    public double getTimeSeconds() {
      return nanosToSeconds(this.timeNanos);
    }
  }
}
