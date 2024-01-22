package com.bb.onebot.entity.qq;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 发送频道消息实体
 * @author ren
 */
@Data
public class SendChannelMessage {
    private String content;

    private String image;

    @JSONField(name = "msg_id")
    private String msgId;

    @JSONField(name = "event_id")
    private String eventId;
}
