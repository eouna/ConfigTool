package com.eouna.configtool.utils;

import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符串工具
 *
 * @author CCL
 * @date 2023/3/8
 */
public class StrUtils {

  /**
   * 小写第一个字符
   *
   * @param str 待转换的字符串
   * @return 转换后字符串
   */
  public static String lowerFirst(String str) {
    char[] charArray = str.toCharArray();
    char upperCharStart = 'A', upperCharEnd = 'Z';
    if (charArray[0] >= upperCharStart && charArray[0] <= upperCharEnd) {
      charArray[0] += 32;
      return new String(charArray);
    }
    return str;
  }

  /**
   * 小写第一个字符
   *
   * @param str 待转换的字符串
   * @return 转换后字符串
   */
  public static String upperFirst(String str) {
    char[] charArray = str.toCharArray();
    char lowerCharStart = 'a', lowerCharEnd = 'z';
    if (charArray[0] <= lowerCharEnd && charArray[0] >= lowerCharStart) {
      charArray[0] -= 32;
      return new String(charArray);
    }
    return str;
  }

  public static boolean isEmpty(String checkStr) {
    return checkStr == null || checkStr.isEmpty();
  }

  /**
   * 大写检测字符后的一个char值 <br>
   * 字符串比检测字符多时 效率比upperCharWhenMeetSymbolReg差<br>
   * 字符串比检测字符少时 效率比upperCharWhenMeetSymbolReg好<br>
   * 字符串比检测字符相近时 效率和upperCharWhenMeetSymbolReg相当<br>
   *
   * @return 替換之后的值
   */
  public static String upperCharWhenMeetSymbol(String transStr, Character detectChar) {
    char[] transChar = new char[transStr.length()];
    boolean isMeetDetectChar = false;
    int j = 0;
    for (char aChar : transStr.toCharArray()) {
      if (aChar == detectChar) {
        isMeetDetectChar = true;
        continue;
      }
      if (isMeetDetectChar && CharUtils.isAsciiAlphaLower(aChar)) {
        transChar[j++] = (char) (aChar - 32);
      } else {
        transChar[j++] = aChar;
      }
      isMeetDetectChar = false;
    }
    return new String(transChar);
  }

  /**
   * 通过正则将指定字符后的字母转为大写
   *
   * @param transStr 待转换的字符串
   * @param detectChar 检测字符
   * @return 转换后的字符串
   */
  public static String upperCharWhenMeetSymbolReg(String transStr, Character detectChar) {
    Pattern pattern = Pattern.compile(detectChar + "(a-z)");
    Matcher matcher = pattern.matcher(transStr);
    StringBuilder stringBuilder = new StringBuilder();
    while (matcher.find()) {
      matcher.appendReplacement(stringBuilder, matcher.group(1).toUpperCase());
    }
    return stringBuilder.toString();
  }

  /**
   * 替换目标字符串中最后一个找到的字符
   *
   * @param oldStr 旧的字符串
   * @param findStr 需要找到的字符串
   * @param replacedStr 替换字符串
   * @return 替换后的字符串
   */
  public static String replaceLast(String oldStr, String findStr, String replacedStr) {
    int lastIndexOfFindStr = oldStr.lastIndexOf(findStr);
    return oldStr.substring(0, lastIndexOfFindStr - 1)
        + replacedStr
        + oldStr.substring(lastIndexOfFindStr + 1);
  }
}
