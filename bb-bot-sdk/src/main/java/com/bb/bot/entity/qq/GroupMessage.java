package com.bb.bot.entity.qq;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.File;

/**
 * 发送频道消息实体
 * @author ren
 */
@Data
public class GroupMessage {
    private String content;

    /**
     * 消息类型： 0 文本、2 markdown、3 ark 消息、4 embed、7 media 富媒体
     */
    @JSONField(name = "msg_type")
    private Integer msgType;

    //todo
    private Object markdown;
    private Object keyboard;
    private Object ark;

    private UploadMediaResponse media;

    @JSONField(name = "msg_id")
    private String msgId;

    @JSONField(name = "msg_seq")
    private String msgSeq;

    @JSONField(name = "event_id")
    private String eventId;

    @JSONField(serialize = false)
    private File file;

    /**
     * 生成at某人的消息
     *
     * @param userId at某人的用户id
     * @return 封装后的消息实体
     */
    public static String buildAtMessage(String userId) {
        return "<@" + userId + ">";
    }
}
