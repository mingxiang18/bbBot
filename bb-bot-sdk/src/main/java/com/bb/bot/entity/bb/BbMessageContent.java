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
}
