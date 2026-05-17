package com.bb.bot.entity.bb;

import com.bb.bot.constant.BbSendMessageType;
import lombok.Builder;
import lombok.Data;

import java.io.File;

/**
 * 消息内容实体
 * @author ren
 */
@Data
@Builder
public class BbMessageContent {

    /**
     * 消息类型, 支持 text、at、localImage、netImage等
     */
    private String type;

    /**
     * 消息内容
     */
    private Object data;

    /**
     * 附件文件名（仅 localFile / netFile / localImage / netImage 时有意义）
     */
    private String fileName;

    /**
     * 附件 MIME 类型（如 application/pdf、image/png；可空）
     */
    private String mimeType;

    /**
     * 附件字节大小（可空）
     */
    private Long size;

    /**
     * 构建文本消息
     *
     * @param text 消息文本内容
     * @return bbSendMessage 封装后的消息实体
     */
    public static BbMessageContent buildTextContent(String text) {
        return BbMessageContent.builder()
                .type(BbSendMessageType.TEXT)
                .data(text)
                .build();
    }

    /**
     * 从文件路径构建图片消息
     *
     * @param file 消息文件内容
     * @return bbSendMessage 封装后的消息实体
     */
    public static BbMessageContent buildLocalImageMessageContent(File file) {
        return BbMessageContent.builder()
                .type(BbSendMessageType.LOCAL_IMAGE)
                .data(file)
                .build();
    }

    /**
     * 从网络图片路径构建图片消息
     *
     * @param url 网络图片路径构
     * @return bbSendMessage 封装后的消息实体
     */
    public static BbMessageContent buildNetImageMessageContent(String url) {
        return BbMessageContent.builder()
                .type(BbSendMessageType.NET_IMAGE)
                .data(url)
                .build();
    }

    /**
     * 构建at某人消息
     *
     * @param userId at的用户id
     * @return bbSendMessage 封装后的消息实体
     */
    public static BbMessageContent buildAtMessageContent(String userId) {
        return BbMessageContent.builder()
                .type(BbSendMessageType.AT)
                .data(userId)
                .build();
    }

    /**
     * 构建回复消息
     *
     * @param messageId 回复的消息id
     * @return bbSendMessage 封装后的消息实体
     */
    public static BbMessageContent buildReplyMessageContent(String messageId) {
        return BbMessageContent.builder()
                .type(BbSendMessageType.REPLY)
                .data(messageId)
                .build();
    }

    /**
     * 从本地文件构建附件消息（非图片，发送时转 base64）。
     *
     * @param file 本地文件
     * @return 封装后的消息实体
     */
    public static BbMessageContent buildLocalFileMessageContent(File file) {
        return BbMessageContent.builder()
                .type(BbSendMessageType.LOCAL_FILE)
                .data(file)
                .fileName(file == null ? null : file.getName())
                .size(file == null || !file.exists() ? null : file.length())
                .build();
    }

    /**
     * 从网络地址构建附件消息（非图片）。
     *
     * @param url      文件下载地址
     * @param fileName 文件名
     * @return 封装后的消息实体
     */
    public static BbMessageContent buildNetFileMessageContent(String url, String fileName) {
        return BbMessageContent.builder()
                .type(BbSendMessageType.NET_FILE)
                .data(url)
                .fileName(fileName)
                .build();
    }
}
