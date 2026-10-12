package com.eouna.configtool.env;

import com.eouna.configtool.core.annotaion.AutoInject;
import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.logger.LoggerUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语言模板运行环境的检测结果持有者(内存缓存)。
 *
 * <p>本类通过 {@code @Component} 注册到容器中统一管理, 使用时通过 {@code @AutoInject} 注入:
 *
 * <pre>
 * &#64;AutoInject private TemplateEnvContext templateEnvContext;
 *
 * boolean goReady = templateEnvContext.isAvailable("go");
 * TemplateEnvResult result = templateEnvContext.get("go");
 * List&lt;TemplateEnvResult&gt; missing = templateEnvContext.getMissingEnvResults();
 * </pre>
 *
 * <p>启动时由 {@code ConfigToolAppEntrance} 触发一次全量检测, 结果按语言标识缓存在内存中, 供后续(界面提示、
 * 生成前校验、命令行输出等)使用; 可随时调用 {@link #refresh()} 刷新。
 *
 * <p>约定: 后续新增的单例类统一使用 {@code @Component} 注册到容器, 通过 {@code @AutoInject} 注入使用,
 * 不再使用静态单例(enum Singleton / static INSTANCE)。
 *
 * @author CCL
 */
@Component
public class TemplateEnvContext {

  /** 语言标识 -> 检测结果 */
  private final Map<String, TemplateEnvResult> envResults = new ConcurrentHashMap<>();

  /** 是否已经完成过一次检测 */
  private volatile boolean checked;

  /** 环境检测器 */
  @AutoInject private TemplateEnvChecker templateEnvChecker;

  public TemplateEnvContext() {}

  /**
   * 全量检测一次所有支持导出的语言模板的运行环境, 并缓存到内存中。
   *
   * @return 检测结果(语言标识 -> 结果)
   */
  public Map<String, TemplateEnvResult> refresh() {
    Map<String, TemplateEnvResult> results = templateEnvChecker.checkAll();
    envResults.clear();
    envResults.putAll(results);
    checked = true;
    logSummary();
    return getAll();
  }

  /** 是否已经完成过一次检测 */
  public boolean isChecked() {
    return checked;
  }

  /**
   * 获取指定语言的检测结果
   *
   * @param langId 语言标识(与 --lang 一致, 如 java/go/js)
   * @return 检测结果, 未检测过返回null
   */
  public TemplateEnvResult get(String langId) {
    return langId == null ? null : envResults.get(langId.toLowerCase(Locale.ROOT));
  }

  /**
   * 指定语言的运行环境是否可用(未检测过时返回false)
   *
   * @param langId 语言标识
   * @return 是否可用
   */
  public boolean isAvailable(String langId) {
    TemplateEnvResult result = get(langId);
    return result != null && result.isAvailable();
  }

  /** 全部检测结果(语言标识 -> 结果) */
  public Map<String, TemplateEnvResult> getAll() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(envResults));
  }

  /** 需要运行环境但当前不可用的语言 */
  public List<TemplateEnvResult> getMissingEnvResults() {
    List<TemplateEnvResult> missing = new ArrayList<>();
    for (TemplateEnvResult result : getAll().values()) {
      if (result.isEnvRequired() && !result.isAvailable()) {
        missing.add(result);
      }
    }
    return missing;
  }

  /** 所有支持导出的语言标识 */
  public List<String> getSupportedLangIds() {
    return templateEnvChecker.getSupportedLangIds();
  }

  /** 输出检测结果日志 */
  private void logSummary() {
    List<TemplateEnvResult> results = new ArrayList<>(getAll().values());
    StringBuilder summary = new StringBuilder();
    for (int i = 0; i < results.size(); i++) {
      if (i > 0) {
        summary.append(", ");
      }
      summary.append(results.get(i).toShortString());
    }
    LoggerUtils.getLogger().info("语言模板运行环境检测完成: [{}]", summary);
    List<TemplateEnvResult> missing = getMissingEnvResults();
    if (!missing.isEmpty()) {
      StringBuilder missingLang = new StringBuilder();
      for (TemplateEnvResult result : missing) {
        if (missingLang.length() > 0) {
          missingLang.append(", ");
        }
        missingLang.append(result.getLangName()).append('(').append(result.getLangId()).append(')');
      }
      LoggerUtils.getLogger()
          .warn("以下语言模板的运行环境未检测到, 仍可生成代码, 但无法直接编译/运行: [{}]", missingLang);
    }
  }
}