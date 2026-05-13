package com.bb.bot.api;

/**
 * 一次 LLM 流式输出对应的 IM 侧呈现会话。
 *
 * <p>不同平台实现不同呈现策略：</p>
 * <ul>
 *   <li>TG / Discord / BB 私有协议：edit-message（首次 send 拿到 msgId，后续 edit 同一条）</li>
 *   <li>OneBot / QQ 官方：chunked-send（按句号 / 段落 / 字符上限切段连发）</li>
 *   <li>无法识别平台：fallback 缓冲到 complete() 一次性 sendMessage</li>
 * </ul>
 *
 * <p>实现需线程安全 —— appendDelta 通常被 SSE 解析线程调用，complete 由调用方主线程调用。</p>
 */
public interface MessageStreamSession {

    /** 追加一片增量文本。空字符串会被忽略。 */
    void appendDelta(String textDelta);

    /** 流式结束，flush 剩余缓冲并冻结会话。重复调用应是幂等的。 */
    void complete();

    /** 流式异常，最后一次 flush 时附带错误提示。 */
    void fail(Throwable t);
}
