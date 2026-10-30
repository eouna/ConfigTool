package com.eouna.configtool.generator;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 语言模板与数据加载方式的集中配置。
 *
 * <p>新增语言或调整某种语言支持的数据源时, 只需修改本类。界面、命令行和模板生成器都应通过这里判断, 避免在各处重复硬编码语言名称。
 *
 * @author CCL
 */
public final class LanguageDataLoadConfig {

  /** 数据源: excel */
  public static final String SOURCE_EXCEL = "excel";

  /** 数据源: json */
  public static final String SOURCE_JSON = "json";

  /**
   * 语言标识 -> 支持的数据源。
   *
   * <p>js/ts 的读取代码直接消费工具导出的 json 数据, 因此只支持 json; 其余语言同时提供 excel/json 两套数据读取实现。
   */
  private static final Map<String, Set<String>> LANGUAGE_SOURCES;

  static {
    Map<String, Set<String>> languageSources = new LinkedHashMap<>();
    languageSources.put("java", sources(SOURCE_EXCEL, SOURCE_JSON));
    languageSources.put("json", sources(SOURCE_JSON));
    languageSources.put("go", sources(SOURCE_EXCEL, SOURCE_JSON));
    languageSources.put("rust", sources(SOURCE_EXCEL, SOURCE_JSON));
    languageSources.put("csharp", sources(SOURCE_EXCEL, SOURCE_JSON));
    languageSources.put("lua", sources(SOURCE_EXCEL, SOURCE_JSON));
    languageSources.put("cpp", sources(SOURCE_EXCEL, SOURCE_JSON));
    languageSources.put("js", sources(SOURCE_JSON));
    languageSources.put("ts", sources(SOURCE_JSON));
    LANGUAGE_SOURCES = Collections.unmodifiableMap(languageSources);
  }

  private LanguageDataLoadConfig() {}

  /**
   * 获取指定语言支持的数据源。
   *
   * @param langId 语言标识
   * @return 不可变数据源集合, 未知语言返回空集合
   */
  public static Set<String> getSupportedSources(String langId) {
    if (langId == null) {
      return Collections.emptySet();
    }
    return LANGUAGE_SOURCES.getOrDefault(normalize(langId), Collections.emptySet());
  }

  /**
   * 判断是否为已知的数据源标识。
   *
   * @param source 数据源
   * @return 是否为 excel/json
   */
  public static boolean isKnownSource(String source) {
    return SOURCE_EXCEL.equals(normalize(source)) || SOURCE_JSON.equals(normalize(source));
  }

  /**
   * 判断语言是否支持指定数据源。
   *
   * @param langId 语言标识
   * @param source 数据源
   * @return 是否支持
   */
  public static boolean supports(String langId, String source) {
    if (source == null) {
      return false;
    }
    return getSupportedSources(langId).contains(normalize(source));
  }

  /**
   * 获取语言的默认数据源。优先 excel, 仅支持 json 的语言返回 json。
   *
   * @param langId 语言标识
   * @return 默认数据源, 未知语言返回 excel
   */
  public static String getDefaultSource(String langId) {
    Set<String> supportedSources = getSupportedSources(langId);
    if (supportedSources.contains(SOURCE_EXCEL)) {
      return SOURCE_EXCEL;
    }
    return supportedSources.contains(SOURCE_JSON) ? SOURCE_JSON : SOURCE_EXCEL;
  }

  /**
   * 将用户选择的数据源修正为该语言支持的值。
   *
   * @param langId 语言标识
   * @param requestedSource 用户选择的数据源
   * @return 语言支持的数据源
   */
  public static String resolveSource(String langId, String requestedSource) {
    String normalizedSource = normalize(requestedSource);
    return supports(langId, normalizedSource) ? normalizedSource : getDefaultSource(langId);
  }

  /**
   * 为多个语言寻找共同可用的数据源。
   *
   * <p>未指定数据源时优先沿用工具默认的 excel; 显式指定但当前语言组合不支持时, 再按 json、excel 的顺序寻找共同项。例如 java 默认仍为 excel, 而
   * java+ts 会自动落到 json。
   *
   * @param langIds 语言标识集合
   * @param requestedSource 用户选择的数据源
   * @return 所有语言都支持的数据源; 不存在时返回 null
   */
  public static String resolveCommonSource(Collection<String> langIds, String requestedSource) {
    if (langIds == null || langIds.isEmpty()) {
      return normalize(requestedSource);
    }
    String normalizedSource = normalize(requestedSource);
    if (normalizedSource.isEmpty()) {
      for (String candidate : List.of(SOURCE_EXCEL, SOURCE_JSON)) {
        if (allSupport(langIds, candidate)) {
          return candidate;
        }
      }
      return null;
    }
    if (allSupport(langIds, normalizedSource)) {
      return normalizedSource;
    }
    for (String candidate : List.of(SOURCE_JSON, SOURCE_EXCEL)) {
      if (allSupport(langIds, candidate)) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * 语言是否必须依赖 json 数据。
   *
   * @param langId 语言标识
   * @return 不支持 excel 且支持 json 时返回 true
   */
  public static boolean requiresJsonData(String langId) {
    return supports(langId, SOURCE_JSON) && !supports(langId, SOURCE_EXCEL);
  }

  private static boolean allSupport(Collection<String> langIds, String source) {
    for (String langId : langIds) {
      if (!supports(langId, source)) {
        return false;
      }
    }
    return true;
  }

  private static Set<String> sources(String... sources) {
    Set<String> sourceSet = new LinkedHashSet<>();
    Collections.addAll(sourceSet, sources);
    return Collections.unmodifiableSet(sourceSet);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
