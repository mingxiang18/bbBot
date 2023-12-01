package com.bb.onebot.entity.oneBot;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 消息实体
 * @author ren
 */
@Data
@Builder
public class Message {

    /**
     * 消息类型, 支持 private、group , 分别对应私聊、群组, 如不传入, 则根据传入的 *_id 参数判断
     */
    @JSONField(name = "message_type")
    private String messageType;

    /**
     * 对方 QQ 号 ( 消息类型为 private 时需要 )
     */
    @JSONField(name = "user_id")
    private String userId;

    /**
     * 群号 ( 消息类型为 group 时需要 )
     */
    @JSONField(name = "group_id")
    private String groupId;

    /**
     * 消息内容
     */
    @JSONField(name = "message")
    private List<MessageContent> message;

    /**
     * 消息内容是否作为纯文本发送 ( 即不解析 CQ 码 ) , 只在 message 字段是字符串时有效
     */
    @JSONField(name = "auto_escape")
    private boolean autoEscape;

}
