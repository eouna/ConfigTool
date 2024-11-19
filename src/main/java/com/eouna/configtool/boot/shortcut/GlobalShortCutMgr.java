package com.eouna.configtool.boot.shortcut;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.eouna.configtool.common.FxApplicationContextHolder;
import com.eouna.configtool.core.annotaion.ApplicationInitialized;
import com.eouna.configtool.core.boot.context.ApplicationContext;
import com.eouna.configtool.core.factory.anno.Component;
import com.eouna.configtool.core.window.WindowManager;
import com.eouna.configtool.ui.controllers.ExcelSearchController;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

/**
 * 全局快捷键组合键
 *
 * @author CCL
 * @date 2023/5/29
 */
@Component
public class GlobalShortCutMgr {

  /** 快捷键缓存 */
  private static final Map<Scene, List<KeyCodeCombine>> SHORT_CUT_CACHE = new ConcurrentHashMap<>();

  /** 场景 */
  private Scene mainScene;

  /** 搜索快捷键 */
  private static final KeyCodeCombination SEARCH_SHORTCUT =
      new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN);

  /** 初始化默认的快捷键 */
  @ApplicationInitialized
  public void initDefaultCombinationKeys() {
    ApplicationContext applicationContext = FxApplicationContextHolder.getInstance().getContext();
    // 主舞台
    Stage mainStage = applicationContext.getMainStage();
    // 注册搜索键
    registerKeyCombineToScene(
        (mainScene = mainStage.getScene()),
        new KeyCodeCombine(
            "EXCEL搜索",
            SEARCH_SHORTCUT,
            () -> WindowManager.getInstance().openWindow(ExcelSearchController.class)));
  }

  /**
   * 给场景注册快捷键
   *
   * @param scene 场景
   * @param keyCodeCombine 快捷键
   */
  public void registerKeyCombineToScene(Scene scene, KeyCodeCombine keyCodeCombine) {
    Objects.requireNonNull(scene, "需要注册场景的不能为空");
    // 不能添加重复键
    if (containShortCut(scene, keyCodeCombine)) {
      return;
    }
    synchronized (SHORT_CUT_CACHE){
      // 注册组合键及其对应的回调
      scene.getAccelerators().put(keyCodeCombine.getKeyCodeCombine(), keyCodeCombine.callBack);
      SHORT_CUT_CACHE.computeIfAbsent(scene, k -> new ArrayList<>()).add(keyCodeCombine);
    }
  }

  /**
   * 是否包含快捷键
   *
   * @param keyCodeCombine 快捷键信息
   * @return 是否包含
   */
  public boolean containShortCut(Scene scene, KeyCodeCombine keyCodeCombine) {
    if (keyCodeCombine == null || keyCodeCombine.getKeyCodeCombine() == null) {
      return false;
    }
    List<KeyCodeCombine> keyCodeCombines =
        new ArrayList<>(SHORT_CUT_CACHE.get(scene == null ? mainScene : scene));
    for (KeyCodeCombine codeCombination : keyCodeCombines) {
      KeyCodeCombination keyCodeCombination = codeCombination.keyCodeCombine;
      if (keyCodeCombination.getCode() == keyCodeCombine.getKeyCodeCombine().getCode()
          && keyCodeCombination.getMeta().equals(keyCodeCombine.getKeyCodeCombine().getMeta())) {
        return true;
      }
    }
    return false;
  }

  public static class KeyCodeCombine {
    /** 组合键的描述 */
    private String combineCodeDesc;
    /** 键的组合 */
    private KeyCodeCombination keyCodeCombine;

    private Runnable callBack;

    public KeyCodeCombine(
        String combineCodeDesc, KeyCodeCombination keyCodeCombine, Runnable callBack) {
      this.combineCodeDesc = combineCodeDesc;
      this.keyCodeCombine = keyCodeCombine;
      this.callBack = callBack;
    }

    public String getCombineCodeDesc() {
      return combineCodeDesc;
    }

    public void setCombineCodeDesc(String combineCodeDesc) {
      this.combineCodeDesc = combineCodeDesc;
    }

    public KeyCodeCombination getKeyCodeCombine() {
      return keyCodeCombine;
    }

    public void setKeyCodeCombine(KeyCodeCombination keyCodeCombine) {
      this.keyCodeCombine = keyCodeCombine;
    }

    public Runnable getCallBack() {
      return callBack;
    }

    public void setCallBack(Runnable callBack) {
      this.callBack = callBack;
    }
  }
}
