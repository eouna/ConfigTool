package com.eouna.configtool.core.window;

import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.utils.ResourceUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Scene;

/**
 * 全局窗口主题管理器。
 *
 * <p>该类由容器作为单例组件创建和缓存。窗口控制器创建场景时会自动注册并应用当前主题，主题切换后，管理器会同步刷新所有已注册场景，因此后续新增窗口或主题时不需要逐个窗口编写切换逻辑。
 */
@Component
public final class WindowThemeManager {

  /** 已注册主题。使用 LinkedHashMap 保留注册顺序，便于后续生成主题菜单。 */
  private final Map<String, ThemeDefinition> themes = new LinkedHashMap<>();

  /** 已创建的窗口场景。使用弱引用避免窗口关闭后阻止场景回收。 */
  private final Map<Scene, Boolean> registeredScenes = new WeakHashMap<>();

  /** 主题切换监听器，供菜单选中状态等外围组件同步。 */
  private final List<Consumer<String>> themeChangeListeners = new CopyOnWriteArrayList<>();

  private String currentThemeId;

  public WindowThemeManager() {}

  /**
   * 注册主题。
   *
   * @param id 主题唯一标识
   * @param displayName 主题显示名称
   * @param resourcePaths 相对于应用资源根目录的 CSS 路径，例如 ui/theme/neumorphism.css
   */
  public synchronized void registerTheme(String id, String displayName, String... resourcePaths) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("主题 id 不能为空");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("主题名称不能为空");
    }
    if (resourcePaths == null || resourcePaths.length == 0) {
      throw new IllegalArgumentException("主题至少需要一个 CSS 文件");
    }
    List<String> stylesheets = new ArrayList<>(resourcePaths.length);
    for (String resourcePath : resourcePaths) {
      stylesheets.add(ResourceUtils.getFullResourceUrl(resourcePath));
    }
    themes.put(id, new ThemeDefinition(id, displayName, Collections.unmodifiableList(stylesheets)));
    if (currentThemeId == null) {
      currentThemeId = id;
    }
  }

  /** 切换当前主题。 */
  public void setTheme(String themeId) {
    final List<Scene> scenes;
    synchronized (this) {
      if (!themes.containsKey(themeId)) {
        throw new IllegalArgumentException("未注册的主题: " + themeId);
      }
      if (Objects.equals(currentThemeId, themeId)) {
        return;
      }
      currentThemeId = themeId;
      scenes = new ArrayList<>(registeredScenes.keySet());
    }
    runOnFxThread(
        () -> {
          for (Scene scene : scenes) {
            applyThemeToScene(scene, themeId);
          }
          for (Consumer<String> listener : themeChangeListeners) {
            listener.accept(themeId);
          }
        });
  }

  /** 将当前主题应用到指定场景。 */
  public void applyTheme(Scene scene) {
    if (scene == null) {
      return;
    }
    final String themeId;
    synchronized (this) {
      if (currentThemeId == null) {
        return;
      }
      themeId = currentThemeId;
      registeredScenes.put(scene, Boolean.TRUE);
    }
    runOnFxThread(() -> applyThemeToScene(scene, themeId));
  }

  public synchronized String getCurrentThemeId() {
    return currentThemeId;
  }

  public synchronized ThemeDefinition getTheme(String themeId) {
    return themes.get(themeId);
  }

  public synchronized List<ThemeDefinition> getRegisteredThemes() {
    return List.copyOf(themes.values());
  }

  public void addThemeChangeListener(Consumer<String> listener) {
    if (listener != null) {
      themeChangeListeners.add(listener);
      String themeId = getCurrentThemeId();
      if (themeId != null) {
        runOnFxThread(() -> listener.accept(themeId));
      }
    }
  }

  public void removeThemeChangeListener(Consumer<String> listener) {
    themeChangeListeners.remove(listener);
  }

  private void applyThemeToScene(Scene scene, String themeId) {
    ThemeDefinition targetTheme;
    List<String> allThemeStylesheets = new ArrayList<>();
    synchronized (this) {
      targetTheme = themes.get(themeId);
      if (targetTheme == null) {
        return;
      }
      for (ThemeDefinition definition : themes.values()) {
        allThemeStylesheets.addAll(definition.stylesheets());
      }
      registeredScenes.put(scene, Boolean.TRUE);
    }
    scene.getStylesheets().removeAll(allThemeStylesheets);
    for (String stylesheet : targetTheme.stylesheets()) {
      if (!scene.getStylesheets().contains(stylesheet)) {
        scene.getStylesheets().add(stylesheet);
      }
    }
  }

  private void runOnFxThread(Runnable action) {
    if (Platform.isFxApplicationThread()) {
      action.run();
    } else {
      Platform.runLater(action);
    }
  }

  /** 主题定义。 */
  public record ThemeDefinition(String id, String displayName, List<String> stylesheets) {}
}
