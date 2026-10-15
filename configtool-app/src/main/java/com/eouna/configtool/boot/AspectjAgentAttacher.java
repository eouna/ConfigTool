package com.eouna.configtool.boot;

/**
 * 独立的attach辅助进程: 把 aspectjweaver 作为 java agent 挂到目标JVM(启动本工具的那个进程)。
 *
 * <p>JDK 9+ 起JVM不允许自己attach自己(需要 {@code -Djdk.attach.allowAttachSelf=true}), 因此由子进程来完成attach;
 * 用法: {@code AspectjAgentAttacher <pid> <agentJar>}。
 *
 * <p>这里用反射调用 {@code com.sun.tools.attach.VirtualMachine}, 避免在 module-info 里额外 requires
 * {@code jdk.attach}(本进程按类路径启动, 反射可见即可)。
 *
 * @author CCL
 */
public final class AspectjAgentAttacher {

  private AspectjAgentAttacher() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("用法: AspectjAgentAttacher <pid> <agentJar>");
      System.exit(2);
      return;
    }
    String targetPid = args[0];
    String agentJar = args[1];
    Class<?> virtualMachineClass = Class.forName("com.sun.tools.attach.VirtualMachine");
    Object virtualMachine =
        virtualMachineClass.getMethod("attach", String.class).invoke(null, targetPid);
    try {
      virtualMachineClass
          .getMethod("loadAgent", String.class, String.class)
          .invoke(virtualMachine, agentJar, "");
      System.out.println("aspectjweaver agent loaded: " + agentJar);
    } finally {
      virtualMachineClass.getMethod("detach").invoke(virtualMachine);
    }
  }
}
