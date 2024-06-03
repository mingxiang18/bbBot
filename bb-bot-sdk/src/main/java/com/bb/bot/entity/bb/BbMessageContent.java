package com.bb.bot.entity.bb;

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
     */
    public static BbMessageContent buildTextContent(String text) {
        return BbMessageContent.builder()
                .type("text")
                .data(text)
                .build();
    }

    /**
     * 从文件路径构建图片消息
     */
    public static BbMessageContent buildLocalImageMessageContent(File file) {
        return BbMessageContent.builder()
                .type("localImage")
                .data(file)
                .build();
    }

    /**
     * 从网络图片路径构建图片消息
     */
    public static BbMessageContent buildNetImageMessageContent(String url) {
        return BbMessageContent.builder()
                .type("netImage")
                .data(url)
                .build();
    }

    /**
     * 构建@某人消息
     */
    public static BbMessageContent buildAtMessageContent(String userId) {
        return BbMessageContent.builder()
                .type("at")
                .data(userId)
                .build();
    }
}
