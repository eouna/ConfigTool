package com.eouna.configtool.core.logger;

import com.eouna.configtool.core.exceptions.InitConstructException;
import com.eouna.configtool.core.utils.BeanUtils;
import com.eouna.configtool.core.window.IWindowLogger;
import javafx.scene.text.TextFlow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author CCL
 * @version : [v1.0]
 * @className : [TextAreaLogFactory]
 * @description :  带文本区域的日志工厂
 * @createTime : [2026/4/27 19:11]
 */
public class TextAreaLogFactory {

    private static final Map<Class<?>, TextAreaLogger> textAreaLoggerMap = new ConcurrentHashMap<>();


    /**
     * 尝试向文本logger打日志
     *
     * @param calledClass class对象
     */
    public static void infoToTextLogger(Class<?> calledClass, String msg, Object... args) {
        TextAreaLogger textAreaLogger = null;
        try {
            textAreaLogger = getLogger(calledClass, TextAreaLogger.class, null);
        } catch (Exception ignored) {
        }
        if (textAreaLogger != null) {
            textAreaLogger.info(msg, args);
        } else {
            LoggerUtils.getLogger().info(msg, args);
        }
    }

    /**
     * 尝试向文本logger打日志
     *
     * @param calledClass class对象
     */
    public static void successToTextLogger(Class<?> calledClass, String msg, Object... args) {
        TextAreaLogger textAreaLogger = null;
        try {
            textAreaLogger = getLogger(calledClass, TextAreaLogger.class, null);
        } catch (Exception ignored) {
        }
        if (textAreaLogger != null) {
            textAreaLogger.success(msg, args);
        } else {
            LoggerUtils.getLogger().info(msg, args);
        }
    }

    /**
     * 尝试向文本logger打日志
     *
     * @param calledClass class对象
     */
    public static void errorToTextLogger(Class<?> calledClass, String msg, Object... args) {
        TextAreaLogger textAreaLogger = null;
        try {
            textAreaLogger = getLogger(calledClass, TextAreaLogger.class, null);
        } catch (Exception ignored) {
        }
        if (textAreaLogger != null) {
            textAreaLogger.error(msg, args);
        } else {
            LoggerUtils.getLogger().error(msg, args);
        }
    }

    /**
     * 获取Logger
     *
     * @param textFlow 日志显示区域
     * @return 日志对象
     */
    public synchronized static TextAreaLogger getLogger(Class<?> calledClass, TextFlow textFlow) {
        return getLogger(calledClass, TextAreaLogger.class, textFlow);
    }

    /**
     * 获取Logger
     *
     * @param loggerClass 日志class
     * @param textFlow    日志显示区域
     * @param <LOGGER>    日志类型
     * @return 日志对象
     */
    public synchronized static <LOGGER extends TextAreaLogger> LOGGER getLogger(
        Class<?> calledClass, Class<LOGGER> loggerClass, TextFlow textFlow) {
        LOGGER textAreaLogger = (LOGGER) textAreaLoggerMap.get(calledClass);
        if (textAreaLogger != null) {
            return textAreaLogger;
        }
        try {
            textAreaLogger = BeanUtils.getClassInstance(loggerClass, textFlow);
            textAreaLoggerMap.put(calledClass, textAreaLogger);
            return textAreaLogger;
        } catch (InitConstructException e) {
            throw new RuntimeException(e);
        }
    }
}
