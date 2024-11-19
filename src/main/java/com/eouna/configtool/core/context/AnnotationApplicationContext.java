package com.eouna.configtool.core.context;

import com.eouna.configtool.core.factory.bean.GeneralBeanDefinition;
import com.eouna.configtool.core.utils.ClassUtils;

/**
 * 应用程序上下文
 *
 * @author CCL
 * @date 2023/9/15
 */
public class AnnotationApplicationContext extends GenericApplicationContext
    implements AnnotationContextRegistry {

  /** bean定义 */
  protected final AnnotationBeanDefinitionReader reader;

  protected final ClassPathBeanDefinitionScanner scanner;

  public AnnotationApplicationContext() {
    super();
    reader = new AnnotationBeanDefinitionReader(this, getEnvironment());
    scanner = new ClassPathBeanDefinitionScanner();
    registerDefaultProcessor();
  }

  private void registerDefaultProcessor() {
    String classPostProcessor = ClassUtils.getClassFullName(ConfigurationClassPostHooker.class);
    if (!containBeanDefinition(classPostProcessor)) {
      GeneralBeanDefinition generalBeanDefinition = new GeneralBeanDefinition();
      generalBeanDefinition.setBeanClass(ConfigurationClassPostHooker.class);
      registerBeanDefinition(classPostProcessor, generalBeanDefinition);
    }
  }

  @Override
  public void register(Class<?>... componentClass) {}

  @Override
  public void scan(String... packageNames) {}
}
