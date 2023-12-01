package com.bb.onebot.entity.oneBot;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * @author ren
 */
@Data
public class ReceiveNotice {
    /**
     * 提醒类型
     * notify-提醒
     */
    @JSONField(name = "notice_type")
    private String notify;

    /**
     * 提醒类型子类型
     * poke-戳一戳
     */
    @JSONField(name = "sub_type")
    private String subType;

    @JSONField(name = "user_id")
    private String userId;

    @JSONField(name = "group_id")
    private String groupId;

    @JSONField(name = "sender_id")
    private String senderId;

    @JSONField(name = "target_id")
    private String targetId;
}
