package com.eouna.configtool.core.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 线程安全的lruMap
 *
 * @author CCL
 * @date 2023/7/6
 */
public class LruCacheMap<K, V> extends LinkedHashMap<K, V> {

  /** 默认加载因子 当(tableSize / DEFAULT_LOAD_FACTOR) + 1 > threshold 时发生扩容 */
  private static final float DEFAULT_LOAD_FACTOR = 0.75f;

  /** 最大容量 */
  private volatile int maxCapacity;

  private final ReentrantLock reentrantLock = new ReentrantLock();

  public LruCacheMap(int maxCapacity) {
    super(16, DEFAULT_LOAD_FACTOR, true);
    this.maxCapacity = maxCapacity;
  }

  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
    return size() > maxCapacity;
  }

  @Override
  public V put(K key, V value) {
    reentrantLock.lock();
    try {
      return super.put(key, value);
    } finally {
      reentrantLock.unlock();
    }
  }

  @Override
  public boolean remove(Object key, Object value) {
    reentrantLock.lock();
    try {
      return super.remove(key, value);
    } finally {
      reentrantLock.unlock();
    }
  }

  @Override
  public V get(Object key) {
    reentrantLock.lock();
    try {
      return super.get(key);
    } finally {
      reentrantLock.unlock();
    }
  }

  @Override
  public void clear() {
    reentrantLock.lock();
    try {
      super.clear();
    } finally {
      reentrantLock.unlock();
    }
  }

  @Override
  public int size() {
    reentrantLock.lock();
    try {
      return super.size();
    } finally {
      reentrantLock.unlock();
    }
  }

  public int getMaxCapacity() {
    return maxCapacity;
  }

  public void setMaxCapacity(int maxCapacity) {
    this.maxCapacity = maxCapacity;
  }
}
