package com.bb.bot.entity.oneBot;

import lombok.Builder;
import lombok.Data;

/**
 * 动作请求
 * @author ren
 */
@Data
@Builder
public class Action {
    /**
     * 动作名称
     */
    private String action;

    /**
     * 动作参数
     */
    private Object params;

    /**
     * 构建发送私聊消息动作
     */
    public static Action buildPrivateMessageSendAction(Message message) {
        return Action.builder()
                .action("send_private_msg")
                .params(message)
                .build();
    }

    /**
     * 构建发送群聊消息动作
     */
    public static Action buildGroupMessageSendAction(Message message) {
        return Action.builder()
                .action("send_group_msg")
                .params(message)
                .build();
    }
}
