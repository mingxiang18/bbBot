package com.bb.bot.entity.qq;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.File;

/**
 * 发送频道消息实体
 * @author ren
 */
@Data
public class ChannelMessage {
    private String content;

    private String image;

    @JSONField(name = "msg_id")
    private String msgId;

    @JSONField(name = "event_id")
    private String eventId;

    @JSONField(serialize = false)
    private File file;

    /**
     * 生成@某人的消息
     */
    public static String buildAtMessage(String userId) {
        return "<@" + userId + ">";
    }
}
