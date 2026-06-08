package com.eouna.configtool.core.logger;

import com.eouna.configtool.core.window.IWindowLogger;
import com.eouna.configtool.core.logger.LoggerUtils.LogLevel;
import javafx.scene.text.TextFlow;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * 日志文本框logger
 *
 * @author CCL
 * @date 2023/4/6
 */
@Setter
@Getter
public class TextAreaLogger implements IWindowLogger {

    /**
     * 日志显示区域
     */
    protected TextFlow loggerShowAreaUndertaker;

    /**
     * 当日志显示区域刷新后的回调
     */
    protected Runnable callbackAfterFresh;

    public TextAreaLogger(TextFlow specifyLoggerArea) {
        this.loggerShowAreaUndertaker = specifyLoggerArea;
    }

    @Override
    public void info(String msg, Object... arrays) {
        LoggerUtils.getInstance()
            .appendLogToTextarea(loggerShowAreaUndertaker, LogLevel.INFO, msg, callbackAfterFresh, arrays);
    }

    @Override
    public void success(String msg, Object... arrays) {
        LoggerUtils.getInstance()
            .appendLogToTextarea(loggerShowAreaUndertaker, LogLevel.SUCCESS, msg, callbackAfterFresh, arrays);
    }

    @Override
    public void debug(String debugMsg, Object... arrays) {
        LoggerUtils.getInstance()
            .appendLogToTextarea(loggerShowAreaUndertaker, LogLevel.DEBUG, debugMsg, callbackAfterFresh);
    }

    @Override
    public void warn(String warnMsg, Object... arrays) {
        LoggerUtils.getInstance().appendLogToTextarea(
            loggerShowAreaUndertaker, LogLevel.WARN, warnMsg, callbackAfterFresh);
    }

    @Override
    public void error(String errorMsg, Object... arrays) {
        LoggerUtils.getInstance()
            .appendLogToTextarea(loggerShowAreaUndertaker, LogLevel.ERROR, errorMsg, callbackAfterFresh, arrays);
    }

    @Override
    public void error(String errorMsg, Exception e, Object... arrays) {
        LoggerUtils.getInstance()
            .appendLogToTextarea(
                loggerShowAreaUndertaker,
                LogLevel.ERROR,
                errorMsg + " exception: \n" + ExceptionUtils.getStackTrace(e),
                callbackAfterFresh,
                arrays);
    }
}
