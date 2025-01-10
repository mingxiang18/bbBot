package com.bb.bot.entity.oneBot;

import com.bb.bot.entity.oneBot.messageData.*;
import lombok.Builder;
import lombok.Data;

/**
 * 消息内容实体
 * @author ren
 */
@Data
@Builder
public class MessageContent {

    /**
     * 消息类型, 支持 text、face、image等
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
     * @return 封装后的消息实体
     */
    public static MessageContent buildTextContent(String text) {
        return MessageContent.builder()
                .type("text")
                .data(new TextMessageData(text))
                .build();
    }

    /**
     * 构建表情消息
     *
     * @param id 表情id
     * @return 封装后的消息实体
     */
    public static MessageContent buildFaceMessageContent(String id) {
        return MessageContent.builder()
                .type("face")
                .data(new FaceMessageData(id))
                .build();
    }

    /**
     * 从文件路径构建图片消息
     *
     * @param path 文件路径
     * @return 封装后的消息实体
     */
    public static MessageContent buildImageMessageContentFromPath(String path) {
        return MessageContent.builder()
                .type("image")
                .data(new ImageMessageData(path))
                .build();
    }

    /**
     * 从Base64字符串构建图片消息
     *
     * @param base64Image Base64字符串
     * @return 封装后的消息实体
     */
    public static MessageContent buildImageMessageContentFromBase64(String base64Image) {
        return MessageContent.builder()
                .type("image")
                .data(new ImageMessageData("base64://" + base64Image))
                .build();
    }

    /**
     * 构建at某人消息
     *
     * @param qq at某人的qq号码
     * @return 封装后的消息实体
     */
    public static MessageContent buildAtMessageContent(String qq) {
        return MessageContent.builder()
                .type("at")
                .data(new AtMessageData(qq))
                .build();
    }

    /**
     * 构建戳一戳消息
     *
     * @param qq 某人的qq号码
     * @return 封装后的消息实体
     */
    public static MessageContent buildPokeMessageContent(String qq) {
        return MessageContent.builder()
                .type("poke")
                .data(new PokeMessageData(qq))
                .build();
    }

    /**
     * 构建抖一抖，戳一戳消息(无法使用)
     *
     * @return 封装后的消息实体
     */
    public static MessageContent buildShakeMessageContent() {
        return MessageContent.builder()
                .type("shake")
                .build();
    }

    /**
     * 构建猜拳魔法表情
     *
     * @return 封装后的消息实体
     */
    public static MessageContent buildRpsContent() {
        return MessageContent.builder()
                .type("rps")
                .build();
    }

    /**
     * 构建掷骰子魔法表情(无法使用)
     *
     * @return 封装后的消息实体
     */
    public static MessageContent buildDiceContent() {
        return MessageContent.builder()
                .type("dice")
                .build();
    }
}
