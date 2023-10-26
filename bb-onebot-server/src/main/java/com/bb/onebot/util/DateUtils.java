package com.bb.onebot.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间转换工具
 * @author ren
 */
public class DateUtils {

    public static DateTimeFormatter timePattern = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public static DateTimeFormatter normalTimePattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static DateTimeFormatter timePatternNoSecond = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static DateTimeFormatter timePatternHourAndMinute = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 将日期时间字符串从英国时间转换到北京时间，并返回格式yyyy-MM-dd HH:mm-HH:mm的字符串
     * @author ren
     */
    public static String convertUTCTimeToShowString(String startTime, String endTime) {
        //字符串时间转换成时间类型
        return convertDateTimeString(startTime, 8, timePatternNoSecond) + "-" + convertDateTimeString(endTime, 8, timePatternHourAndMinute);
    }

    /**
     * 将日期时间字符串从英国时间转换到北京时间，并返回格式yyyy-MM-dd HH:mm:ss-yyyy-MM-dd HH:mm:ss的字符串
     * @author ren
     */
    public static String convertUTCTimeToALLShowString(String startTime, String endTime) {
        //字符串时间转换成时间类型
        return convertDateTimeString(startTime, 8, timePatternNoSecond) + " - " + convertDateTimeString(endTime, 8, timePatternNoSecond);
    }

    /**
     * 将日期时间字符串从标准格式转换到显示的yyyy-MM-dd HH:mm:ss
     * @param hourOffset 时区不同的小时偏移量
     * @author ren
     */
    public static String convertDateTimeString(String dateTime, int hourOffset, DateTimeFormatter pattern) {
        //字符串时间转换成时间类型
        LocalDateTime date = LocalDateTime.parse(dateTime, timePattern).plusHours(hourOffset);
        return date.format(pattern);
    }
}
