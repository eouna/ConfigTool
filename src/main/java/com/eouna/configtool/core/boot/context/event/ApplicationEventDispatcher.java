package com.eouna.configtool.core.boot.context.event;

import com.eouna.configtool.core.annotaion.NoneNull;
import com.eouna.configtool.core.annotaion.OrderedComparator;
import com.eouna.configtool.core.event.ApplicationEvent;
import com.eouna.configtool.core.factory.IBeanFactory;
import com.eouna.configtool.core.context.ApplicationListener;
import com.eouna.configtool.core.factory.exceptions.NoSuchBeanException;
import com.eouna.configtool.core.utils.FieldUtils;
import com.eouna.configtool.core.utils.LruCacheMap;
import com.google.common.base.Objects;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 程序事件分发器
 *
 * @author CCL
 * @date 2023/7/6
 */
public final class ApplicationEventDispatcher {

  /** logger */
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** 监听器 */
  private final Set<ApplicationListener<? extends ApplicationEvent>> listeners =
      new LinkedHashSet<>();

  /** 设置了监听的 gentemppath.bean 名字 因为在设置时bean还未初始化 所以使用 name进行注册 */
  private final Set<String> listenerBeanNameSet = new LinkedHashSet<>();

  /** 监听器缓存 */
  private final Map<EventCacheKey, List<ApplicationListener<? extends ApplicationEvent>>>
      listenerCache = new LruCacheMap<>(100);

  /** bean工厂 */
  private IBeanFactory beanFactory;

  /** 事件执行器 可指定线程或者线程池执行指定的事件 */
  private Executor executor;

  /**
   * 注册监听器
   *
   * @param listener 事件监听
   */
  public void registerListeners(ApplicationListener<?> listener) {
    synchronized (listeners) {
      listeners.add(listener);
      // 此处没法获取event的resource具体类,先清除
      listenerCache.clear();
    }
  }

  /**
   * 移除监听
   *
   * @param listener 监听器
   */
  public void removeListeners(ApplicationListener<?> listener) {
    synchronized (listeners) {
      listeners.remove(listener);
      listenerCache.clear();
    }
  }

  /**
   * 通过bean的名字注册事件监听
   *
   * @param listenerBeanName 监听器bean名字
   */
  public void registerListeners(String listenerBeanName) {
    synchronized (listenerBeanNameSet) {
      listenerBeanNameSet.add(listenerBeanName);
      listenerCache.clear();
    }
  }

  /**
   * 分发事件
   *
   * @param event event
   */
  public void dispatchEvent(@NoneNull ApplicationEvent event) {
    // 构建事件类
    EventCacheKey eventClass = new EventCacheKey(event.getClass(), event.getSource().getClass());
    List<ApplicationListener<ApplicationEvent>> listenersOfEvent = new ArrayList<>();
    // 先从缓存中拿取
    if (listenerCache.containsKey(eventClass) && !listenerCache.get(eventClass).isEmpty()) {
      listenersOfEvent.addAll(
          listenerCache.get(eventClass).stream()
              .map(l -> (ApplicationListener<ApplicationEvent>) l)
              .collect(Collectors.toList()));
    } else {
      // 从监听列表中查找并加入缓存
      for (ApplicationListener<? extends ApplicationEvent> listener : getListeners()) {
        EventCacheKey eventCacheKey = getCacheKeyByListener(listener);
        if (eventCacheKey.eventClass.equals(eventClass.eventClass)) {
          // 添加缓存
          listenerCache
              .computeIfAbsent(eventCacheKey, k -> new CopyOnWriteArrayList<>())
              .add(listener);
          listenersOfEvent.add((ApplicationListener<ApplicationEvent>) listener);
        }
      }
    }
    List<ApplicationListener<ApplicationEvent>> sortedEvent = new ArrayList<>(listenersOfEvent);
    OrderedComparator.sort(sortedEvent);
    // 通知事件对应的监听器 事件发生
    for (ApplicationListener<ApplicationEvent> listener : sortedEvent) {
      if (executor != null) {
        executor.execute(() -> listener.onEventHappen(event));
      } else {
        listener.onEventHappen(event);
      }
    }
  }

  /**
   * 获取注册的监听器
   *
   * @return 监听器列表
   */
  private Set<ApplicationListener<? extends ApplicationEvent>> getListeners() {
    Set<ApplicationListener<? extends ApplicationEvent>> applicationListeners =
        new LinkedHashSet<>(listeners);
    if (listenerBeanNameSet.isEmpty() || beanFactory == null) {
      return applicationListeners;
    }
    // 通过bean获取监听名
    for (String listenerBeanName : listenerBeanNameSet) {
      try {
        ApplicationListener<? extends ApplicationEvent> listener =
            beanFactory.getBean(listenerBeanName);
        applicationListeners.add(listener);
      } catch (NoSuchBeanException noSuchBeanException) {
        logger.debug("gentemppath.bean listener: {} not found", listenerBeanName);
      }
    }
    return applicationListeners;
  }

  /**
   * 获取监听者对应的事件类型
   *
   * @param listener
   * @return
   */
  private Class<? extends ApplicationEvent> getEventClassOfListener(
      ApplicationListener<?> listener) {
    // 获取监听者对应的事件类型
    ParameterizedType typeParameterOfListener =
        (ParameterizedType) listener.getClass().getGenericInterfaces()[0];
    return (Class<? extends ApplicationEvent>) typeParameterOfListener.getActualTypeArguments()[0];
  }

  /**
   * 通过监听器获取事件的监听key
   *
   * @param listener 监听器
   * @return 事件key
   */
  private EventCacheKey getCacheKeyByListener(
      ApplicationListener<? extends ApplicationEvent> listener) {
    Class<? extends ApplicationEvent> eventClassOfListener = getEventClassOfListener(listener);
    Field field;
    try {
      field = FieldUtils.getClassField(eventClassOfListener, "source");
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
    Class<?> sourceClass = field.getClass();
    return new EventCacheKey(eventClassOfListener, sourceClass);
  }

  private static class EventCacheKey {
    private final Class<?> eventClass;
    private final Class<?> sourceClass;

    public EventCacheKey(Class<?> eventClass, Class<?> sourceClass) {
      this.eventClass = eventClass;
      this.sourceClass = sourceClass;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof EventCacheKey that)) {
        return false;
      }
      return Objects.equal(eventClass, that.eventClass)
          && Objects.equal(sourceClass, that.sourceClass);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(eventClass, sourceClass);
    }
  }

  public void setBeanFactory(IBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  public void setExecutor(Executor executor) {
    this.executor = executor;
  }
}
