package com.bb.bot.constant;

/**
 * BB 私有协议流式帧状态。
 *
 * <p>一次逻辑回复由同一 {@code streamId} 串起的多帧组成：</p>
 * <ul>
 *   <li>{@link #START} —— 首帧，客户端据此创建一个可被后续帧续写的消息气泡</li>
 *   <li>{@link #DELTA} —— 增量帧，客户端把 data 追加到该气泡</li>
 *   <li>{@link #END}   —— 末帧，客户端追加剩余 data 并把该气泡定型</li>
 * </ul>
 *
 * <p>当 {@code streamState} 为 {@code null} 时是一条普通的一次成型消息（含短回复优化）。</p>
 *
 * @author ren
 */
public class BbStreamState {

    public static final String START = "start";
    public static final String DELTA = "delta";
    public static final String END = "end";

    private BbStreamState() {}
}
