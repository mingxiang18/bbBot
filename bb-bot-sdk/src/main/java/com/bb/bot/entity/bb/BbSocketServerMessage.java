package com.bb.bot.entity.bb;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * bb服务端发送消息对象
 * @author ren
 */
@Data
@NoArgsConstructor
public class BbSocketServerMessage {
    /**
     * 消息类型
     * private-私人，group-群组
     */
    private String messageType;
    /**
     * 发送者id
     */
    private String userId;
    /**
     * 群组id
     */
    private String groupId;
    /**
     * 要回复的消息唯一id
     */
    private String receiveMessageId;
    /**
     * 回复序号，同一条消息回复多次时递增
     */
    private int messageSeq = 1;
    /**
     * 流式回复 id：同一逻辑回复的多帧共享同一值；非流式消息为 null。
     */
    private String streamId;
    /**
     * 流式帧状态：start / delta / end；非流式消息为 null。
     *
     * @see com.bb.bot.constant.BbStreamState
     */
    private String streamState;
    /**
     * 消息内容
     */
    private List<BbMessageContent> messageList = new ArrayList<>();
}
