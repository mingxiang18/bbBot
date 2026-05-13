package com.bb.bot.api;

import lombok.extern.slf4j.Slf4j;

/**
 * 提供节流 + 缓冲基础设施的流式呈现基类。
 *
 * <p>策略：累计字符到 {@link #minFlushChars} 或距上次 flush 超过 {@link #minFlushIntervalMs}
 * 时触发一次 flush；complete / fail 触发最终 flush。</p>
 */
@Slf4j
public abstract class AbstractMessageStreamSession implements MessageStreamSession {

    /** 完整内容缓冲（edit 类适配器用）。 */
    protected final StringBuilder buffer = new StringBuilder();

    /** 未发送的增量片段（chunked 类适配器用）。 */
    protected final StringBuilder pendingChunk = new StringBuilder();

    protected long lastFlushAt = 0L;
    protected boolean completed = false;

    /** 最小 flush 间隔（毫秒）。子类可覆盖匹配平台限制。 */
    protected long minFlushIntervalMs = 1500L;

    /** 累计字符达到该阈值就 flush。 */
    protected int minFlushChars = 60;

    @Override
    public synchronized void appendDelta(String textDelta) {
        if (completed || textDelta == null || textDelta.isEmpty()) {
            return;
        }
        buffer.append(textDelta);
        pendingChunk.append(textDelta);
        if (shouldFlush()) {
            doFlushSafely(false);
            lastFlushAt = System.currentTimeMillis();
        }
    }

    @Override
    public synchronized void complete() {
        if (completed) {
            return;
        }
        completed = true;
        doFlushSafely(true);
    }

    @Override
    public synchronized void fail(Throwable t) {
        if (completed) {
            return;
        }
        log.warn("流式输出异常，已发送 {} 字符，将附错误信息收尾", buffer.length(), t);
        String errSuffix = "\n[流式中断：" + (t == null ? "未知错误" : t.getMessage()) + "]";
        buffer.append(errSuffix);
        pendingChunk.append(errSuffix);
        completed = true;
        doFlushSafely(true);
    }

    protected boolean shouldFlush() {
        long now = System.currentTimeMillis();
        return pendingChunk.length() >= minFlushChars
                || (lastFlushAt > 0 && (now - lastFlushAt) >= minFlushIntervalMs);
    }

    private void doFlushSafely(boolean isFinal) {
        try {
            flush(isFinal);
        } catch (Exception e) {
            log.error("流式 flush 失败，isFinal={}", isFinal, e);
        }
    }

    /**
     * 子类实现：把当前已累计内容呈现到 IM 平台。
     *
     * @param isFinal complete() / fail() 触发的最终 flush 为 true；中途节流 flush 为 false
     */
    protected abstract void flush(boolean isFinal);
}
