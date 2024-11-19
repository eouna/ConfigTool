package ${packageName};

import javax.annotation.processing.Generated;
/**
* 配置表基类
*
* @author CCL
*/
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class BaseCfgBean {

  /** sid */
  protected int ${idName};

  /** 返回sid */
  public int get${idName?cap_first}() {
    return ${idName};
  }
}
