package com.bb.bot.connection.qq;

/**
 * QQ 机器人 WebSocket 网关接收侧 opcode 枚举。
 * 仅承载 {@link QqWebSocketClient#handleMessage} 中用于分支判断的接收 opcode，
 * 取值与协议字面量逐一相等（见各常量注释）。
 *
 * @author ren
 */
public enum QqOpcode {
    /** 0：服务端推送事件（DISPATCH），需结合事件类型 t 进一步分发 */
    DISPATCH(0),
    /** 7：服务端通知客户端重新连接 */
    RECONNECT(7),
    /** 10：网关连接成功后下发的 hello 消息，触发鉴权 */
    HELLO(10),
    /** 11：心跳答复（HEARTBEAT ACK） */
    HEARTBEAT_ACK(11);

    private final int code;

    QqOpcode(int code) {
        this.code = code;
    }

    /**
     * @return 协议 op 字面量
     */
    public int code() {
        return code;
    }

    /**
     * 判断给定 op 是否与本枚举一致。
     *
     * @param op 收到的 op 值
     * @return 相等返回 true
     */
    public boolean matches(int op) {
        return this.code == op;
    }
}
