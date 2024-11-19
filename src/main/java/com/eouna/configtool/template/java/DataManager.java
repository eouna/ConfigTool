package com.eouna.configtool.template.java;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

// import ${cfgBeanPackageName}.*;
// import ${containerPackageName}.*;

import javax.annotation.processing.Generated;

import com.eouna.configtool.template.java.BaseCfgContainer.ContainerExceptionBlocker;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 游戏数据管理器
 *
 * @author CCL
 * @date 2023/3/6
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class DataManager {

  /** logger */
  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** cfgBean和container之间的绑定关系 */
  private Map<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> cfgBeanContainerRelator =
      Collections.emptyMap();

  /** 默认的监听器 */
  private static final IContainerEachLoadListener DEFAULT_LISTENER;

  static {
    DEFAULT_LISTENER =
        new IContainerEachLoadListener() {
          @Override
          public <T extends BaseCfgBean> void loadBefore(BaseCfgContainer<T> baseCfgContainer) {}

          @Override
          public <T extends BaseCfgBean> void loadAfter(BaseCfgContainer<T> baseCfgContainer) {}
        };
  }

  public DataManager() {}

  /**
   * 加载数据
   *
   * @param excelResourcePath excel资源路径
   * @throws Exception e
   */
  public static void loadAllData(String excelResourcePath) throws Exception {
    getInstance().loadAllDataByExcelPath(excelResourcePath, DEFAULT_LISTENER);
  }

  /**
   * 加载数据
   *
   * @param excelResourcePath excel资源路径
   * @param useMultiThreadAcc 是否使用多线程加速加载
   * @throws Exception e
   */
  public static void loadAllData(String excelResourcePath, boolean useMultiThreadAcc)
      throws Exception {
    getInstance().loadAllDataByExcelPath(excelResourcePath, DEFAULT_LISTENER, useMultiThreadAcc);
  }

  /**
   * 加载数据
   *
   * @param resourcePath excel资源路径
   * @param containerLoadListener 每个容器加载时调用
   * @throws Exception e
   */
  public static void loadAllData(
      String resourcePath, IContainerEachLoadListener containerLoadListener) throws Exception {
    getInstance().loadAllDataByExcelPath(resourcePath, containerLoadListener);
  }

  /**
   * 加载数据
   *
   * @param resourcePath excel资源路径
   * @param changedFileList 每个容器加载时调用
   * @throws Exception e
   */
  public static Set<Class<? extends BaseCfgBean>> loadDataByFileList(
      String resourcePath, List<File> changedFileList) throws Exception {
    return getInstance().loadDataByChangeFileList(resourcePath, changedFileList);
  }

  /**
   * 初始化所有容器, 如果配置表过多时可考虑使用反射实现
   *
   * @return 容器map
   */
  public Map<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> initAllContainer() {
    Map<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> containerMap =
        new ConcurrentHashMap<>(8);
    // ===============cfg加载模板开始===================
    // <#list beanAndContainerMap as key,value>
    // containerMap.put(${key}.class, new ${beanAndContainerMap[key]}());
    // </#list>
    // ===============cfg加载模板结束===================
    return containerMap;
  }

  /** 加载每个数据时的数据监听器 */
  public interface IContainerEachLoadListener {

    /**
     * 加载之前
     *
     * @param baseCfgContainer 基础容器
     */
    <T extends BaseCfgBean> void loadBefore(BaseCfgContainer<T> baseCfgContainer);

    /**
     * 加载之后
     *
     * @param baseCfgContainer 容器
     * @throws Exception e
     */
    <T extends BaseCfgBean> void loadAfter(BaseCfgContainer<T> baseCfgContainer) throws Exception;
  }

  /** 最大核心线程数 需要预留一部分核心数给其他逻辑,但最低需要4个线程 */
  private static final int MAX_CORE_THREAD_NUM =
      Math.max(Runtime.getRuntime().availableProcessors() / 2, 4);

  /**
   * 获取线程池
   *
   * @param taskSize 需要加载的任务数量
   * @return 线程池
   */
  protected ThreadPoolExecutor getExcelLoadPoolExecutor(int taskSize) {
    int availableProcessors = Runtime.getRuntime().availableProcessors();
    // 最大容量
    int maxCapacity = taskSize - MAX_CORE_THREAD_NUM;
    AtomicInteger theadNumber = new AtomicInteger(0);
    ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
    ThreadFactory threadFactory =
        r -> new Thread(threadGroup, r, "excel-loader-" + theadNumber.incrementAndGet());
    return new ThreadPoolExecutor(
        MAX_CORE_THREAD_NUM,
        availableProcessors,
        10,
        TimeUnit.SECONDS,
        new LinkedBlockingDeque<>(maxCapacity),
        threadFactory);
  }

  /**
   * 通过excel路径加速
   *
   * @param resourcePath excel资源路径
   * @param containerLoadListener 每个容器加载时调用
   * @throws Exception
   */
  public synchronized void loadAllDataByExcelPath(
      String resourcePath, IContainerEachLoadListener containerLoadListener) throws Exception {
    loadAllDataByExcelPath(resourcePath, containerLoadListener, false);
  }

  /**
   * 加载所有数据 同时只能允许一个线程进行加载
   *
   * @param resourcePath excel资源路径
   * @param containerLoadListener 每个容器加载时调用
   * @param useMultiThreadAcc 是否使用多线程加速加载excel数据
   */
  public synchronized void loadAllDataByExcelPath(
      String resourcePath,
      IContainerEachLoadListener containerLoadListener,
      boolean useMultiThreadAcc)
      throws Exception {
    final String checkedPath = checkAndGetAbsFilePath(resourcePath);
    Map<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> containerMap = initAllContainer();
    long loadStartTime = System.currentTimeMillis();
    // 异常列表
    List<Throwable> exceptions =
        useMultiThreadAcc ? new CopyOnWriteArrayList<>() : new ArrayList<>();
    if (useMultiThreadAcc) {
      ExecutorService executorService = getExcelLoadPoolExecutor(containerMap.size());
      CountDownLatch countDownLatch = new CountDownLatch(containerMap.size());
      for (Entry<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> containerEntry :
          containerMap.entrySet()) {
        BaseCfgContainer<?> cfgContainer = containerEntry.getValue();
        executorService.submit(
            () -> {
              loadSingleContainer(containerLoadListener, checkedPath, cfgContainer, exceptions);
              countDownLatch.countDown();
            });
      }
      // 每个配置表平均等待一秒钟
      if (!countDownLatch.await(containerMap.size(), TimeUnit.SECONDS)) {
        logger.error("配置表加载超时,数量: {}, 等待时间: {}s", containerMap.size(), containerMap.size());
        if (!executorService.isShutdown() && !executorService.isTerminated()) {
          executorService.shutdown();
        }
        throw new RuntimeException("配置表加载超时， 等待时间: " + containerMap.size() + "s, 当前预设配置表平均耗时1s");
      }
      if (!executorService.isShutdown() && !executorService.isTerminated()) {
        executorService.shutdown();
      }
    } else {
      for (Entry<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> containerEntry :
          containerMap.entrySet()) {
        BaseCfgContainer<?> cfgContainer = containerEntry.getValue();
        loadSingleContainer(containerLoadListener, checkedPath, cfgContainer, exceptions);
      }
    }
    if (!exceptions.isEmpty()) {
      // 整理和抛出异常
      throw new Exception(formatExceptionMsg(exceptions));
    } else {
      // 加载时内部没有异常时才算成功
      logger.info(
          "加载配置表成功,加载: {} 个文件共耗时: {}s",
          containerMap.size(),
          (System.currentTimeMillis() - loadStartTime) / 1000f);
      cfgBeanContainerRelator = Collections.unmodifiableMap(containerMap);
    }
  }

  /**
   * 检查文资源文件的合法性
   *
   * @param resourcePath 资源路径
   * @throws IOException IO异常
   */
  private String checkAndGetAbsFilePath(String resourcePath) throws IOException {
    String fullPath;
    // 如果是绝对路径
    if (resourcePath.startsWith("/") || resourcePath.indexOf(":") > 0) {
      fullPath = resourcePath;
    } else {
      fullPath = System.getProperty("user.dir") + "/" + resourcePath;
    }
    File file = new File(fullPath);
    if (!file.exists()) {
      throw new IOException("file: " + resourcePath + " not exists!");
    }
    if (!file.isDirectory()) {
      throw new IllegalArgumentException("resource path must be a directory!");
    }
    return fullPath;
  }

  /**
   * 通过路径加载单个容器
   *
   * @param containerLoadListener 容器加载时的监听器
   * @param resourcePath excel路径
   * @param cfgContainer 配置表容器
   * @param exceptionCollector 异常收集器
   */
  private void loadSingleContainer(
      IContainerEachLoadListener containerLoadListener,
      String resourcePath,
      BaseCfgContainer<?> cfgContainer,
      List<Throwable> exceptionCollector) {
    try {
      logger.info("开始加载配置表: " + cfgContainer.getClass().getSimpleName());
      // 加载前调用
      containerLoadListener.loadBefore(cfgContainer);
      // 加载数据
      cfgContainer.loadData(resourcePath);
      // 加载后调用
      containerLoadListener.loadAfter(cfgContainer);
      logger.info(
          "配置表容器: "
              + cfgContainer.getClass().getSimpleName()
              + " 加载配置表: "
              + String.join(" ", cfgContainer.getExcelNameList())
              + "成功 加载文档数量: "
              + cfgContainer.getCfgBeanMap().size());
    } catch (Exception e) {
      if (e instanceof BaseCfgContainer.ContainerExceptionBlocker) {
        exceptionCollector.addAll(((ContainerExceptionBlocker) e).getExceptions());
      } else {
        exceptionCollector.add(e);
      }
      logger.error(
          "配置表容器: "
              + cfgContainer.getClass().getSimpleName()
              + " 加载配置表: "
              + String.join(" ", cfgContainer.getExcelNameList())
              + "失败");
    }
  }

  /**
   * 通过变化的excel加载数据
   *
   * @param resourcePath 资源路径
   * @param changeFileList 变化文件
   * @return 加载成功的配置列表
   */
  public synchronized Set<Class<? extends BaseCfgBean>> loadDataByChangeFileList(
      String resourcePath, List<File> changeFileList) throws Exception {
    Function<File, String> valueMapper =
        t -> {
          try {
            return DigestUtils.md5Hex(Files.newInputStream(t.toPath()));
          } catch (IOException e) {
            return "";
          }
        };
    // 变化的文件名和文件的MD5
    Map<String, String> fileNameAndMd5Map =
        changeFileList.stream().collect(Collectors.toMap(File::getName, valueMapper));
    List<String> changedFileNameList = new ArrayList<>(fileNameAndMd5Map.keySet());
    // 加载成功的列表
    Set<Class<? extends BaseCfgBean>> loadSuccessBeanClasses = new HashSet<>();
    // 获取需要重新加载的容器列表
    Map<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> needReloadContainer =
        getNeedReloadContainer(changedFileNameList);
    // 异常列表
    List<Throwable> exceptions = new ArrayList<>();
    // 复制一份新的
    Map<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> cfgBeanContainerRelatorTemp =
        new ConcurrentHashMap<>(cfgBeanContainerRelator);
    // 重载需要更新的配置数据
    for (Map.Entry<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> baseCfgContainer :
        needReloadContainer.entrySet()) {
      BaseCfgContainer<? extends BaseCfgBean> baseCfgContainerNew =
          baseCfgContainer.getValue().getNewContainer();
      boolean loadSuccess = false;
      try {
        // 通过容器加载excel文件
        loadSuccess =
            loadSingleChangeFile(
                resourcePath,
                baseCfgContainerNew,
                baseCfgContainer.getValue().getMd5CacheMap(),
                fileNameAndMd5Map);
      } catch (Exception e) {
        if (e instanceof BaseCfgContainer.ContainerExceptionBlocker) {
          exceptions.addAll(((ContainerExceptionBlocker) e).getExceptions());
        } else {
          exceptions.add(e);
        }
        logger.error(
            "配置表容器: "
                + baseCfgContainer.getValue().getClass().getSimpleName()
                + " 加载配置表: "
                + String.join(" ", baseCfgContainer.getValue().getExcelNameList())
                + "失败");
      }
      // 加载成功才写入
      if (loadSuccess) {
        cfgBeanContainerRelatorTemp.put(baseCfgContainer.getKey(), baseCfgContainerNew);
        loadSuccessBeanClasses.add(baseCfgContainer.getKey());
      }
    }
    // 为了不多次放表 如果有加载成功的还是将加载成功的表放入内存中
    if (!loadSuccessBeanClasses.isEmpty()) {
      cfgBeanContainerRelator = Collections.unmodifiableMap(cfgBeanContainerRelatorTemp);
      logger.info("配置表热更结束,替换配置表: " + String.join(",", changedFileNameList) + " 成功");
      return loadSuccessBeanClasses;
    }
    if (!exceptions.isEmpty()) {
      throw new Exception(formatExceptionMsg(exceptions));
    }
    return loadSuccessBeanClasses;
  }

  /**
   * 获取需要重载的配置表容器
   *
   * @param changedFileNameList 文件改变列表
   * @return 需要重新加载的配置表容器
   */
  private Map<Class<? extends BaseCfgBean>, BaseCfgContainer<?>> getNeedReloadContainer(
      List<String> changedFileNameList) {
    return cfgBeanContainerRelator.entrySet().stream()
        .filter(
            baseCfgContainer ->
                changedFileNameList.stream()
                    .anyMatch(
                        (changedFileName) ->
                            baseCfgContainer.getValue().getExcelNameList().stream()
                                .anyMatch(
                                    (containerBindExcelName) ->
                                        containerBindExcelName.contains(changedFileName))))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * 加载单个改变文件
   *
   * @param resourcePath 加载资源路径
   * @param baseCfgContainerNew 新容器
   * @param oldMd5CacheMap 旧容器的md5Map
   * @param newMd5CacheMap 新容器的md5Map
   * @return 是否加载成功
   * @throws Exception e
   */
  private boolean loadSingleChangeFile(
      String resourcePath,
      BaseCfgContainer<?> baseCfgContainerNew,
      Map<String, String> oldMd5CacheMap,
      Map<String, String> newMd5CacheMap)
      throws Exception {
    // 先判断是否有关联表,没有则直接更新
    if (!baseCfgContainerNew.hasRelatedTable()) {
      String excelName = baseCfgContainerNew.getExcelNameList().get(0);
      excelName =
          !excelName.contains("/") ? excelName : excelName.substring(excelName.indexOf("/") + 1);
      excelName =
          !excelName.contains("\\") ? excelName : excelName.substring(excelName.indexOf("\\") + 1);
      String newMd5Code = newMd5CacheMap.get(excelName);
      boolean isNewMd5CodeEmpty = newMd5Code == null || newMd5Code.isEmpty();
      // 包含则说明md5码没有变化不执行更新
      if (isNewMd5CodeEmpty || !oldMd5CacheMap.containsValue(newMd5Code)) {
        baseCfgContainerNew.loadData(resourcePath);
        logger.info(
            "配置表容器: {} 加载配置表: {} 成功",
            baseCfgContainerNew.getClass().getSimpleName(),
            String.join(" ", baseCfgContainerNew.getExcelNameList()));
        return true;
      } else {
        logger.warn(
            "配置表容器: "
                + baseCfgContainerNew.getClass().getSimpleName()
                + " 加载配置表: "
                + String.join(" ", baseCfgContainerNew.getExcelNameList())
                + " 跳过,新文件Md5码: {},老文件Md5码: {} 文件名: {}",
            newMd5Code,
            oldMd5CacheMap.get(excelName),
            excelName);
      }
    } else {
      // 如果是有关联型的配置则直接走逻辑更新
      baseCfgContainerNew.loadData(resourcePath);
      logger.info(
          "配置表容器: {} 加载配置表: {} 成功",
          baseCfgContainerNew.getClass().getSimpleName(),
          String.join(" ", baseCfgContainerNew.getExcelNameList()));
      return true;
    }
    return false;
  }

  /**
   * 异常整理并将异常抛出
   *
   * @param exceptions 异常列表
   */
  private String formatExceptionMsg(List<Throwable> exceptions) {
    // 整理配置表异常统一抛出异常
    StringBuilder exceptionBuilder = new StringBuilder();
    try {
      for (Throwable exception : exceptions) {
        if (exception instanceof BaseCfgContainer.ExcelDataParseException
            || exception instanceof BaseCfgContainer.ExcelFormatException) {
          // 内部异常优先打印
          exceptionBuilder = new StringBuilder(exception.getMessage() + "\n" + exceptionBuilder);
          continue;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        exception.printStackTrace(pw);
        exceptionBuilder.append(sw.getBuffer().toString()).append("\n");
        sw.close();
        pw.close();
      }
    } catch (IOException e) {
      exceptionBuilder.append(e);
    }
    return exceptionBuilder.toString();
  }

  /**
   * 通过配置表bean获取配置表容器
   *
   * @param baseCfgBeanClass 配置表beanClass
   * @return 配置表容器
   * @param <T> 容器类型
   * @param <Bfg> 配置表类型
   */
  public <T extends BaseCfgContainer<Bfg>, Bfg extends BaseCfgBean> T getCfgContainer(
      Class<Bfg> baseCfgBeanClass) {
    if (cfgBeanContainerRelator.containsKey(baseCfgBeanClass)) {
      return (T) cfgBeanContainerRelator.get(baseCfgBeanClass);
    } else {
      throw new RuntimeException(
          "can`t found cfg gentemppath.bean class: "
              + baseCfgBeanClass.getSimpleName()
              + " container");
    }
  }

  /**
   * 获取所有缓存的 cfgBean class
   *
   * @return class列表
   */
  public Set<Class<? extends BaseCfgBean>> getAllCfgBeanClasses() {
    return Collections.unmodifiableSet(new HashSet<>(cfgBeanContainerRelator.keySet()));
  }
  // ===============cfg获取方法模板开始===================

  public static BaseCfgBean getCfgBean(int key) {
    return getInstance().getCfgContainer(BaseCfgBean.class).getCfgBeanMap().get(key);
  }

  public static Map<Integer, BaseCfgBean> getCfgBeanMap() {
    return getInstance().getCfgContainer(BaseCfgBean.class).getCfgBeanMap();
  }

  public static List<BaseCfgBean> getCfgBeanList() {
    return getInstance().getCfgContainer(BaseCfgBean.class).getCfgBeanList();
  }

  // ===============cfg获取方法模板结束===================

  // ===============cfg获取方法模板开始===================

  // <#list beanAndContainerMap as key,value>
  //  public static ${key} get${key}(int key) {
  //    return getInstance().getCfgContainer(${key}.class).getCfgBeanMap().get(key);
  //  }
  //
  //  public static Map<Integer, ${key}> get${key}BeanMap() {
  //    return getInstance().getCfgContainer(${key}.class).getCfgBeanMap();
  //  }
  //
  //  public static List<${key}> get${key}BeanList() {
  //    return getInstance().getCfgContainer(${key}.class).getCfgBeanList();
  //  }
  // </#list>
  // ===============cfg获取方法模板结束===================

  /**
   * 单例
   *
   * @return GameDataManager
   */
  public static DataManager getInstance() {
    return Singleton.INSTANCE.getInstance();
  }

  enum Singleton {
    // 单例
    INSTANCE;

    private final DataManager instance;

    Singleton() {
      this.instance = new DataManager();
    }

    public DataManager getInstance() {
      return instance;
    }
  }
}
