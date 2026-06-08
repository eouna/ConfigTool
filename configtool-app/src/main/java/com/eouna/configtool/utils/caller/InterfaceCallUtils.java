package com.eouna.configtool.utils.caller;

import com.eouna.configtool.core.watcher.TimeConsumeWatcher;
import com.eouna.configtool.core.utils.ClassOfPackageUtils;
import com.eouna.configtool.core.logger.LoggerUtils;
import com.eouna.configtool.utils.StrUtils;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 接口调用工具 需要调用者保证线程安全<br>
 *
 * @author CCL
 * @date 2023/2/15
 */
@SuppressWarnings("unchecked")
public class InterfaceCallUtils {

  /** 功能运行最大超时时间ms */
  private static final int FUNCTION_CALL_DO_OVER_TIME = 200;

  private static final Logger log = LoggerFactory.getLogger(InterfaceCallUtils.class);

  /** 调用缓存 */
  private static final Map<String, Set<Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();

  /**
   * 通过接口调用具体的实现类 默认使用order进行排序使用从小到大排序
   *
   * @param interfaceClass 接口类
   * @param callEachAction 对每个实现类的调用行为
   * @param <T> T
   */
  public static <T> void callByFunctionalInterfaceType(
      Class<T> interfaceClass, IInterfaceCallEachAction<T> callEachAction) throws Exception {

    if (interfaceClass.getAnnotation(FunctionalInterface.class) == null) {
      throw new RuntimeException("it`s not functional interface can`t call this method");
    }

    String methodName = interfaceClass.getMethods()[0].getName();
    DefaultMethodCallPriorityComparator<T> comparator =
        new DefaultMethodCallPriorityComparator<>(methodName);

    callByInterfaceType(interfaceClass, callEachAction, comparator);
  }

  /**
   * 通过接口调用具体的实现类 默认使用order进行排序使用从小到大排序 该方法非线程安全！！！！
   *
   * @param interfaceClass 接口类
   * @param callEachAction 对每个实现类的调用行为
   * @param methodName 调用方法
   * @param <T> T
   */
  public static <T> void callByInterfaceType(
      Class<T> interfaceClass, String methodName, IInterfaceCallEachAction<T> callEachAction)
      throws Exception {

    if (StringUtils.isEmpty(methodName)) {
      throw new RuntimeException("caller method is empty");
    }

    if (Arrays.stream(interfaceClass.getMethods())
        .noneMatch(method -> method.getName().equals(methodName))) {
      throw new RuntimeException(
          "interface class: "
              + interfaceClass.getSimpleName()
              + " not found method: "
              + methodName);
    }

    DefaultMethodCallPriorityComparator<T> comparator =
        new DefaultMethodCallPriorityComparator<>(methodName);

    callByInterfaceType(interfaceClass, callEachAction, comparator);
  }

  /**
   * 通过接口调用具体的实现类 该方法非线程安全！！！！
   *
   * @param interfaceClass 接口类
   * @param callEachAction 对每个实现类的调用行为
   * @param methodPriorityComparator 每个调用函数的比较器通过order注解进行比较
   * @param <T> T
   */
  public static <T> void callByInterfaceType(
      Class<T> interfaceClass,
      IInterfaceCallEachAction<T> callEachAction,
      Comparator<Class<T>> methodPriorityComparator)
      throws Exception {

    Objects.requireNonNull(interfaceClass, "call class is null");
    Objects.requireNonNull(callEachAction, "call action is null");
    Objects.requireNonNull(methodPriorityComparator, "method comparator is null");

    if (!CLASS_CACHE.containsKey(interfaceClass.getSimpleName())) {
      Set<Class<?>> interfaceClasses =
          ClassOfPackageUtils.getClassesByPackage(
              InterfaceCallUtils.class.getClassLoader(), "com.lsy");
      interfaceClasses =
          interfaceClasses.stream()
              .sorted((o1, o2) -> methodPriorityComparator.compare((Class<T>) o1, (Class<T>) o2))
              .collect(Collectors.toCollection(LinkedHashSet::new));
      CLASS_CACHE.putIfAbsent(
          interfaceClass.getSimpleName(), Collections.unmodifiableSet(interfaceClasses));
    }

    for (Class<?> interfaceCacheClass : CLASS_CACHE.get(interfaceClass.getSimpleName())) {
      if (!Modifier.isAbstract(interfaceCacheClass.getModifiers())
          && !Modifier.isInterface(interfaceCacheClass.getModifiers())) {
        String lowerFirst = StrUtils.lowerFirst(interfaceCacheClass.getSimpleName());
        // 如果能找到bean
        /*if (SpringContextUtils.getInstance().getContext().containsBean(lowerFirst)) {
          T classBean = (T) SpringContextUtils.getInstance().getContext().getBean(lowerFirst);
          // 调用前行为
          callEachAction.callBefore(classBean);
          try {
            // 调用具体的实现方法
            callEachAction.call(classBean);
          } catch (Exception exception) {
            log.error(
                "调用方法: " + interfaceClass.getSimpleName() + " 发生异常: " + exception.getMessage(),
                exception);
          }
          // 调用后行为
          callEachAction.afterCall(classBean);
        }*/
      }
    }
  }

  /** 默认超时时间 */
  public static <T> void doCallInterfaceWatchTime(
      Class<T> interfaceClass, AbstractTimeInferFaceCallImpl<T> callEachAction) {
    doCallInterfaceWatchTime(interfaceClass, callEachAction, FUNCTION_CALL_DO_OVER_TIME);
  }

  /**
   * 带运行时间观察的方法调用
   *
   * @param interfaceClass 接口Class
   * @param callEachAction 实现抽象时间检测
   * @param <T> 类型
   */
  public static <T> void doCallInterfaceWatchTime(
      Class<T> interfaceClass, AbstractTimeInferFaceCallImpl<T> callEachAction, int printWhenOverTime) {
    TimeConsumeWatcher timeConsumeWatcher =
        new TimeConsumeWatcher(interfaceClass.getSimpleName() + "Watcher");
    callEachAction.setTimeConsumeWatcher(timeConsumeWatcher);
    try {
      InterfaceCallUtils.callByFunctionalInterfaceType(interfaceClass, callEachAction);
      String printStr =
          timeConsumeWatcher.prettyPrint(taskInfo -> taskInfo.getTimeMillis() > printWhenOverTime);
      if (!org.apache.commons.lang3.StringUtils.isEmpty(printStr)) {
        log.warn(printStr);
      }
    } catch (Exception exception) {
      LoggerUtils.getLogger().error("方法调用失败", exception);
    }
  }
}
