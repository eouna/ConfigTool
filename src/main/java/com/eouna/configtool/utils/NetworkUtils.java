package com.eouna.configtool.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * 网络工具
 *
 * @author CCL
 * @date 2023/12/5
 */
public class NetworkUtils {

  /**
   * 获取局域网内的用户真实IP
   *
   * @return IP地址
   */
  public static Set<String> getRealIpList() {
    try {
      Set<String> realIpList = new HashSet<>();
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      while (networkInterfaces.hasMoreElements()) {
        NetworkInterface networkInterface = networkInterfaces.nextElement();
        Enumeration<InetAddress> enumeration = networkInterface.getInetAddresses();
        while (enumeration.hasMoreElements()) {
          InetAddress inetAddress = enumeration.nextElement();
          // only ipv4 address
          if (!(inetAddress instanceof Inet4Address)) {
            continue;
          }
          String ip = inetAddress.getHostAddress();
          // avoid loopback address
          if (!networkInterface.isLoopback()
              && networkInterface.isUp()
              && !isVirtualInterface(networkInterface)) {
            LoggerUtils.getLogger()
                .info(
                    "Detected Net Interface: {} IP: {} DisName: [{}]",
                    networkInterface.getName(),
                    ip,
                    networkInterface.getDisplayName());
            realIpList.add(ip);
          }
        }
      }
      LoggerUtils.getLogger().info("realIp: {}", String.join(",", realIpList));
      return realIpList;
    } catch (Exception e) {
      LoggerUtils.getLogger().error("query local net interface err", e);
    }
    return Collections.singleton("UNKNOWN");
  }

  /**
   * 是否是虚拟网卡,主要排除vm和virtualbox等
   *
   * @param networkInterface 网卡对象
   * @return 是否是虚拟网卡
   */
  private static boolean isVirtualInterface(NetworkInterface networkInterface) {
    if (networkInterface.isVirtual()) {
      return true;
    }
    String displayName = networkInterface.getDisplayName();
    String interfaceName = networkInterface.getName();
    return displayName.contains("Virtual")
        || displayName.contains("virtual")
        || displayName.contains("vm")
        || displayName.contains("Vm")
        || interfaceName.contains("Virtual")
        || interfaceName.contains("virtual")
        || interfaceName.contains("vm")
        || interfaceName.contains("Vm");
  }
}
