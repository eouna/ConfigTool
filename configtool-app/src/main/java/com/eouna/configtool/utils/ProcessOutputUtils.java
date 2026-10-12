package com.eouna.configtool.utils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 子进程输出解码工具。
 *
 * <p>不同工具链的输出编码不一致: 跨平台工具链(go/node/dotnet等)多为UTF-8,
 * 而Windows下的工具链(如MSVC的cl)会输出当前系统代码页(中文Windows为GBK)。
 * 这里统一按"先UTF-8, 失败后回退系统原生编码"的顺序解码, 避免界面/日志出现乱码。
 *
 * @author CCL
 */
public final class ProcessOutputUtils {

  private ProcessOutputUtils() {}

  /**
   * 解码子进程输出
   *
   * @param outputBytes 原始字节
   * @return 解码后的文本, 无法完全解码时用替换字符兜底
   */
  public static String decode(byte[] outputBytes) {
    if (outputBytes == null || outputBytes.length == 0) {
      return "";
    }
    try {
      return decodeStrict(outputBytes, StandardCharsets.UTF_8);
    } catch (CharacterCodingException ignored) {
      Charset nativeCharset = resolveNativeCharset();
      if (!StandardCharsets.UTF_8.equals(nativeCharset)) {
        try {
          return decodeStrict(outputBytes, nativeCharset);
        } catch (CharacterCodingException ignoredAgain) {
          // 继续使用带替换字符的解码, 保证不会因为异常中断调用方流程
        }
      }
      return new String(outputBytes, StandardCharsets.UTF_8);
    }
  }

  /** 严格按指定编码解码, 编码不匹配时抛出异常以便回退 */
  private static String decodeStrict(byte[] outputBytes, Charset charset)
      throws CharacterCodingException {
    CharsetDecoder decoder =
        charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    CharBuffer chars = decoder.decode(ByteBuffer.wrap(outputBytes));
    return chars.toString();
  }

  /** 读取运行环境对应的原生编码, 例如中文Windows下的GBK */
  public static Charset resolveNativeCharset() {
    for (String propertyName :
        new String[] {"native.encoding", "sun.jnu.encoding", "file.encoding"}) {
      String charsetName = System.getProperty(propertyName);
      if (charsetName == null || charsetName.isBlank()) {
        continue;
      }
      try {
        return Charset.forName(charsetName);
      } catch (Exception ignored) {
        // 尝试下一个编码来源
      }
    }
    return Charset.defaultCharset();
  }
}
