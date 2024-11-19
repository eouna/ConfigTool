package com.eouna.configtool.core.context.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.eouna.configtool.core.factory.config.BeanFactoryPostHooker;
import com.eouna.configtool.core.factory.config.BeanPostHooker;
import com.eouna.configtool.core.factory.support.BeanDefinitionRegistryPostHooker;
import com.eouna.configtool.core.factory.support.DefaultBeanFactory;

/**
 * 钩子注册和管理处理类
 *
 * @author CCL
 * @date 2023/9/28
 */
public class PostHookerRegistrationDelegate {

  /**
   * 调用bean工厂的钩子
   *
   * @param beanFactory bean工厂
   * @param postHookerList 处理钩子列表
   */
  public static void invokeBeanFactoryPostHookers(
      DefaultBeanFactory beanFactory, List<BeanFactoryPostHooker> postHookerList) {
    // 已经调用过的钩子,避免重复调用
    Set<String> alreadyPostedHookers = new HashSet<>();
    // 首先调用bean定义处理钩子
    // 普通bean工厂处理钩子
    List<BeanFactoryPostHooker> beanFactoryPostHookers = new ArrayList<>();
    // bean定义注册处理钩子
    List<BeanDefinitionRegistryPostHooker> beanDefinitionRegistryPostHookers = new ArrayList<>();
    for (BeanFactoryPostHooker beanPostHooker : postHookerList) {
      if (beanPostHooker instanceof BeanDefinitionRegistryPostHooker) {
        beanDefinitionRegistryPostHookers.add((BeanDefinitionRegistryPostHooker) beanPostHooker);
      } else {
        beanFactoryPostHookers.add(beanPostHooker);
      }
    }
    // 已经注册好的钩子
    List<BeanDefinitionRegistryPostHooker> registeredBeanDefinitionRegistryPostHookers =
        new ArrayList<>();
    // 获取已注册且实现了BeanDefinitionRegistryPostHooker的bean名字
    List<String> beanDefinitionHookers =
        beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostHooker.class);
    for (String beanDefinitionHooker : beanDefinitionHookers) {
      registeredBeanDefinitionRegistryPostHookers.add(
          beanFactory.getBean(beanDefinitionHooker, BeanDefinitionRegistryPostHooker.class));
      alreadyPostedHookers.add(beanDefinitionHooker);
    }

  }
}
