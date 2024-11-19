package com.eouna.configtool.utils;

import com.eouna.configtool.configholder.ConfigDataBean;
import com.eouna.configtool.configholder.ConfigDataBean.ServerConnectInfo;
import com.eouna.configtool.configholder.SystemConfigHolder;
import com.eouna.configtool.core.window.IWindowLogger;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务器连接工具
 *
 * @author CCL
 * @date 2023/4/4
 */
public class JschUtils {

  /**
   * 获取会话
   *
   * @return 会话
   */
  public static Session getSession(ServerConnectInfo serverConnectInfo) {
    JSch jSch = new JSch();
    Session session = null;
    try {
      session =
          jSch.getSession(
              serverConnectInfo.getUsername(),
              serverConnectInfo.getServerIp(),
              serverConnectInfo.getPort());
      session.setPassword(serverConnectInfo.getUserPass());
      session.setConfig("StrictHostKeyChecking", "no");
      // 超时时间十秒种
      session.setTimeout(10000);
      session.connect();
      if (session.isConnected()) {
        return session;
      }
      LoggerUtils.getTextareaLogger().error("连接服务器: {} 失败", serverConnectInfo.getServerIp());
      return null;
    } catch (JSchException jSchException) {
      LoggerUtils.getTextareaLogger().error("连接服务器异常", jSchException);
    }
    return session;
  }

  /**
   * 获取piped输入流中的数据
   *
   * @param inputStream 输入流
   * @return 数据
   * @throws IOException e
   */
  public static String getStringFromPipedInputStream(InputStream inputStream) throws IOException {
    StringBuilder stringMsg = new StringBuilder();
    byte[] tempBuf = new byte[1024];
    while (inputStream.available() > 0) {
      int res = inputStream.read(tempBuf, 0, tempBuf.length);
      if (res < 0) {
        break;
      }
      stringMsg.append(new String(tempBuf));
    }
    return stringMsg.toString();
  }

  public static void uploadFile(Session session, File zipFile, String destPath) {
    uploadFile(session, zipFile, destPath, null);
  }

  /**
   * 上传文件到远端服务器
   *
   * @param zipFile 压缩文件
   * @param destPath 目标路径
   * @param session 服务器会话
   * @param monitor 文件进度检测
   */
  public static void uploadFile(
      Session session, File zipFile, String destPath, SftpProgressMonitor monitor) {
    if (!zipFile.exists() || zipFile.isDirectory()) {
      LoggerUtils.getTextareaLogger().error("文件: {} 不存在或者是一个文件夹", zipFile);
      return;
    }
    ChannelSftp channelSftp = null;

    try {
      if (session == null) {
        throw new RuntimeException("服务器连接失败");
      }
      channelSftp = (ChannelSftp) session.openChannel("sftp");
      channelSftp.connect();
      channelSftp.put(new FileInputStream(zipFile), destPath + zipFile.getName(), monitor);
    } catch (SftpException sftpException) {
      LoggerUtils.getTextareaLogger().error("上传文件失败", sftpException);
    } catch (FileNotFoundException fileNotFoundException) {
      LoggerUtils.getTextareaLogger()
          .error("查找文件: " + zipFile.getPath() + " 失败", fileNotFoundException);
    } catch (JSchException jSchException) {
      LoggerUtils.getTextareaLogger().error("连接失败 ", jSchException);
    } finally {
      if (channelSftp != null && !channelSftp.isClosed()) {
        channelSftp.disconnect();
      }
    }
  }

  /**
   * 执行服务器远程命令
   *
   * @param session session
   * @param command 执行的命令
   */
  public static void executeCommand(Session session, String command) {
    executeCommand(session, command, null);
  }

  /**
   * 执行服务器远程命令
   *
   * @param session session
   * @param command 执行的命令
   * @param callBack 执行完成后的回调
   */
  public static void executeCommand(
      Session session, String command, CommandExecuteCallBack callBack) {
    ChannelExec channelExec = null;
    try {
      if (session == null) {
        throw new RuntimeException("服务器连接失败");
      }
      channelExec = (ChannelExec) session.openChannel("exec");
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      channelExec.setInputStream(null);
      channelExec.setErrStream(outputStream);
      channelExec.setCommand(command);
      channelExec.connect();
      while (true) {
        String res = JschUtils.getStringFromPipedInputStream(channelExec.getInputStream());
        if (channelExec.isClosed()) {
          String error = outputStream.toString();
          LoggerUtils.getLogger().info("执行结果: " + res + " error: " + error);
          if (callBack != null) {
            callBack.callBack(command, res, error);
          }
          break;
        }
      }
    } catch (JSchException | IOException jSchException) {
      LoggerUtils.getTextareaLogger().error("连接失败 ", jSchException);
    } finally {
      if (channelExec != null) {
        channelExec.disconnect();
      }
    }
  }

  /** 命令执行回调接口 */
  public interface CommandExecuteCallBack {
    /**
     * 执行回调
     *
     * @param executeRes 执行结果
     * @param errMsg 错误信息
     * @param command 命令
     */
    void callBack(String command, String executeRes, String errMsg);
  }

  public static class DefaultPutSftpMonitor implements SftpProgressMonitor {
    final IWindowLogger logger;
    // 结束标志
    final AtomicBoolean endFlag = new AtomicBoolean(false);
    // 结束回调
    Runnable finishedCallBack;

    public DefaultPutSftpMonitor(IWindowLogger logger) {
      this.logger = logger;
    }

    public void setFinishedCallBack(Runnable finishedCallBack) {
      this.finishedCallBack = finishedCallBack;
    }

    @Override
    public void init(int op, String src, String dest, long max) {}

    @Override
    public boolean count(long count) {
      return true;
    }

    public AtomicBoolean getEndFlag() {
      return endFlag;
    }

    @Override
    public void end() {
      endFlag.set(true);
      if (finishedCallBack != null) {
        finishedCallBack.run();
      }
    }
  }
}
