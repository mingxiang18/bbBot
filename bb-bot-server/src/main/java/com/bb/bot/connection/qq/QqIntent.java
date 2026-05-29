package com.bb.bot.connection.qq;

/**
 * QQ 机器人 WebSocket 鉴权所需的 intents 位标志枚举。
 * 取值与协议位移字面量逐一相等（见各常量注释），
 * 供 {@link QqWebSocketClient} 在 IDENTIFY 鉴权时按位或组合。
 *
 * @author ren
 */
public enum QqIntent {
    /** 1<<12：频道私信 */
    DIRECT_MESSAGE(1 << 12),
    /** 1<<25：群/单聊事件(含 C2C 私聊) */
    GROUP_AND_C2C_EVENT(1 << 25),
    /** 1<<30：频道 @ 消息 */
    CHANNEL_AT_MESSAGE(1 << 30);

    private final int value;

    QqIntent(int value) {
        this.value = value;
    }

    /**
     * @return 协议 intent 位标志字面量
     */
    public int value() {
        return value;
    }

    /**
     * 按位或组合多个 intent。
     *
     * @param intents 待组合的 intent
     * @return 组合后的位掩码
     */
    public static int combine(QqIntent... intents) {
        int result = 0;
        for (QqIntent intent : intents) {
            result |= intent.value;
        }
        return result;
    }
}
