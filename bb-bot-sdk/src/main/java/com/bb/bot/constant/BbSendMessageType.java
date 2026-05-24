package com.bb.bot.constant;

/**
 * 消息类型常量
 * @author ren
 */
public class BbSendMessageType {

    /**
     * 文本消息
     */
    public static final String TEXT = "text";

    /**
     * at消息
     */
    public static final String AT = "at";
    /**
     * 本地图片
     */
    public static final String LOCAL_IMAGE = "localImage";
    /**
     * 网络图片
     */
    public static final String NET_IMAGE = "netImage";
    /**
     * 回复消息
     */
    public static final String REPLY = "reply";
    /**
     * 本地文件（非图片附件，data 为 File，发送时转 base64）
     */
    public static final String LOCAL_FILE = "localFile";
    /**
     * 网络文件（非图片附件，data 为下载 URL）
     */
    public static final String NET_FILE = "netFile";
}
