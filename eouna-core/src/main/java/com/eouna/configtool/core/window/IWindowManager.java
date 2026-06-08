package com.eouna.configtool.core.window;
/**
 * 窗口管理器接口
 *
 * @author CCL
 * @date 2023/7/27
 */
public interface IWindowManager {

	/**
	 * 通过窗口别名或者窗口类名打开窗口
	 * @param windowAliasOrClassName 窗口类名或者窗口别名
	 * @param args
	 * @return
	 */
	IWindowManager openWindow(String windowAliasOrClassName, Object... args);

	IWindowManager openWindow(Class<? extends BaseWindowController> windowClass, Object... args);

	IWindowManager openWindow(long windowUniqueId, Object... args);

	IWindowManager openOrCreateWindow(String windowAliasOrClassName, Object... args);

	IWindowManager openOrCreateWindow(Class<? extends BaseWindowController> windowClass, Object... args);

	IWindowManager openOrCreateWindow(long windowUniqueId, Object... args);

	<T extends BaseWindowController> T getWindow(String windowAliasOrClassName);

	<T extends BaseWindowController> T getWindow(Class<? extends BaseWindowController> windowClass);

	<T extends BaseWindowController> T getWindow(long windowUniqueId);

	boolean containWindow(String windowAliasOrClassName);

	boolean containWindow(Class<? extends BaseWindowController> windowClass);

	boolean containWindow(long windowUniqueId);

	<T extends BaseWindowController> T closeWindow(String windowAliasOrClassName);

	<T extends BaseWindowController> T closeWindow(Class<? extends BaseWindowController> windowClass);

	<T extends BaseWindowController> T closeWindow(long windowUniqueId);

	<T extends BaseWindowController> void closeWindow(T window);

	void showPreWindow();

	void showPreWindow(BaseWindowController baseWindowController);

	void showPreWindow(long windowUniqueId);

	void showLastWindow();

	void showNextWindow(BaseWindowController baseWindowController);

	void showNextWindow(long windowUniqueId);
}
