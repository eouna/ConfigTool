package com.eouna.configtool.core.context;

import java.lang.management.ManagementFactory;
import java.util.EventObject;
import java.util.concurrent.atomic.AtomicBoolean;

import com.eouna.configtool.core.factory.config.BeanDefinition;
import com.eouna.configtool.core.factory.support.AutowireBeanFactory;
import com.eouna.configtool.core.factory.support.BeanDefinitionRegistry;
import com.eouna.configtool.core.factory.support.DefaultBeanFactory;
import javafx.stage.Stage;

/**
 * 程序上下文
 *
 * @author CCL
 * @date 2023/7/17
 */
public class GenericApplicationContext extends AbstractApplicationContext
    implements BeanDefinitionRegistry {

  /** 刷新状态 */
  private final AtomicBoolean refreshStatus = new AtomicBoolean(false);

  /** 舞台 */
  private Stage stage;

  public GenericApplicationContext() {
    factory = new DefaultBeanFactory();
  }

  @Override
  public int getApplicationPid() {
    return Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
  }

  @Override
  public void pushEvent(EventObject eventObject) {}


  @Override
  protected void refreshBeanFactory() {
    if (!refreshStatus.compareAndSet(false, true)) {
      throw new IllegalStateException("容器重复刷新");
    }
  }

  @Override
  public AutowireBeanFactory getBeanFactory() {
    return factory;
  }

  @Override
  public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
    factory.registerBeanDefinition(beanName, beanDefinition);
  }

  @Override
  public BeanDefinition getBeanDefinition(String beanName) {
    return null;
  }

  @Override
  public boolean containBeanDefinition(String beanName) {
    return false;
  }

  @Override
  public void removeBeanDefinition(String beanName) {}
}
