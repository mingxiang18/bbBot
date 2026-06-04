package com.bb.bot.diagnostics;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

/**
 * 全链路 traceId 上下文。
 *
 * <p>一条入站消息从 {@code BbEventListener} 进入时调用 {@link #start(String)} 生成 traceId 并写入
 * SLF4J {@link MDC}，logback pattern 的 {@code %X{traceId}} 即可把同一条消息在整条链路（去重 → 分发 →
 * 决策 → 发送）上的日志用同一个 id 串起来。异步 handler 线程的 MDC 传递由
 * {@code BotThreadPoolExecutorConfig} 的 TaskDecorator 负责。</p>
 *
 * <p>traceId 由 messageId 派生（同一 messageId → 同一 traceId），这样 QQ 超时重推的重复消息天然
 * 共享 traceId，便于关联；messageId 为空时退回纳秒时钟。</p>
 */
public final class TraceContext {

    /** MDC key，与 logback pattern 中的 {@code %X{traceId}} 对应。 */
    public static final String KEY = "traceId";

    private TraceContext() {}

    /** 生成 traceId 并写入 MDC，返回该 id。 */
    public static String start(String messageId) {
        String id = newId(messageId);
        MDC.put(KEY, id);
        return id;
    }

    /** 由 messageId 派生短 traceId（8 位十六进制）；messageId 为空时退回纳秒时钟。 */
    public static String newId(String messageId) {
        if (StringUtils.isNotBlank(messageId)) {
            return Integer.toHexString(messageId.hashCode() & 0x7fffffff);
        }
        return Long.toHexString(System.nanoTime() & 0xffffffffL);
    }

    /** 当前线程的 traceId，无则 null。 */
    public static String current() {
        return MDC.get(KEY);
    }

    /** 清除当前线程的 traceId。 */
    public static void clear() {
        MDC.remove(KEY);
    }
}
