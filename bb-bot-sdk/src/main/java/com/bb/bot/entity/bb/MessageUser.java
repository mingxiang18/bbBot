package com.bb.bot.entity.bb;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接收消息的发送者对象
 * @author ren
 */
@Data
@NoArgsConstructor
public class MessageUser {
    /**
     * 用户id
     */
    private String userId;
    /**
     * 用户昵称
     */
    private String nickname;
    /**
     * 是否机器人
     */
    private Boolean botFlag = false;

    public MessageUser(String userId) {
        this.userId = userId;
    }

    public MessageUser(String userId, Boolean isBot) {
        this.userId = userId;
        this.botFlag = isBot;
    }
}
