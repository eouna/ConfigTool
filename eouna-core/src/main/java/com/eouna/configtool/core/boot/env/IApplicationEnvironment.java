package com.eouna.configtool.core.boot.env;

import com.eouna.configtool.core.boot.convert.ApplicationConverters;

/**
 * 程序环境接口
 *
 * @author CCL
 */
public interface IApplicationEnvironment {

    /**
     * 保存程序转换器的接口
     *
     * @param applicationConverters Convert
     */
    void setApplicationConvertors(ApplicationConverters applicationConverters);

    /**
     * 设置命令行参数
     *
     * @param commandLineAndArgs 命令行参数
     */
    void setCommandLineAndArgs(CommandLineAndArgs commandLineAndArgs);

    /**
     * 获取命令行参数
     *
     * @return 命令行参数
     */
    CommandLineAndArgs getCommandLineAndArgs();
}
