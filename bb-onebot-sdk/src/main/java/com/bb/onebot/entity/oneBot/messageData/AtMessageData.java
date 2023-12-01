package com.bb.onebot.entity.oneBot.messageData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @某人-消息类型
 * @author ren
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AtMessageData {
    /**
     * @的 QQ 号，all 表示全体成员
     */
    private String qq;
}
