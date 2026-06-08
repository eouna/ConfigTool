package com.eouna.configtool.generator;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模板文件默认线程工厂
 *
 * @author CCL
 * @date 2023/3/6
 */
public class DefaultTemplateGenThreadFactory implements ThreadFactory {
  private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
  private final ThreadGroup group;
  private final AtomicInteger threadNumber = new AtomicInteger(1);
  private final String namePrefix;

  public DefaultTemplateGenThreadFactory() {
    group = Thread.currentThread().getThreadGroup();
    namePrefix = "java-gen-pool-" + POOL_NUMBER.getAndIncrement() + "-thread-";
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
    if (t.isDaemon()) {
      t.setDaemon(false);
    }
    if (t.getPriority() != Thread.NORM_PRIORITY) {
      t.setPriority(Thread.NORM_PRIORITY);
    }
    return t;
  }
}
