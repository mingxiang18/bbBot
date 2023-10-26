package com.bb.onebot.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 接收消息的发送者对象
 * @author ren
 */
@Data
public class ReceiveMessageSender {

    @JSONField(name = "user_id")
    private String userId;

    @JSONField(name = "nickname")
    private String nickname;
}
