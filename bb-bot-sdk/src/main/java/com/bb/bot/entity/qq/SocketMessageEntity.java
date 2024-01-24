package com.bb.bot.entity.qq;

import lombok.Data;

/**
 * socket连接信息实体类
 * @author ren
 */
@Data
public class SocketMessageEntity {
    /**
     * op 指的是 opcode，参考连接维护
     * opcode 列表
     * s 下行消息都会有一个序列号，标识消息的唯一性，客户端需要再发送心跳的时候，携带客户端收到的最新的s。
     * t和d 主要是用在op为 0 Dispatch 的时候。
     * t 代表事件类型。
     * d 代表事件内容，不同事件类型的事件内容格式都不同，请注意识别。
     */

    /**
     * op 指的是 opcode，参考连接维护
     */
    private Integer op;

    /**
     * d 代表事件内容，不同事件类型的事件内容格式都不同，请注意识别。
     */
    private Object d;

    /**
     * s 下行消息都会有一个序列号，标识消息的唯一性，客户端需要再发送心跳的时候，携带客户端收到的最新的s。
     */
    private Integer s;

    /**
     * t 代表事件类型。
     */
    private String t;
}
