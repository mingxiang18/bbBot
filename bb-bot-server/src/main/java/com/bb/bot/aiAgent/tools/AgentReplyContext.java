package com.bb.bot.aiAgent.tools;

/**
 * 当前工具调用所属会话的出站通道（{@link AgentReplySink}）的 ThreadLocal 载体。
 *
 * <p>与 {@link MemoryToolContext} 同理：{@code AiToolExecutor} 在执行具体 @AiTool 方法的
 * 线程上 set，finally 里 clear；send_file 等工具用 {@link #get()} 取出回传通道。
 * 非交互场景（cron 等）不设置 → get() 返回 null，工具据此优雅报错。</p>
 */
public final class AgentReplyContext {

    private static final ThreadLocal<AgentReplySink> SINK = new ThreadLocal<>();

    private AgentReplyContext() {}

    public static void set(AgentReplySink sink) {
        SINK.set(sink);
    }

    /** 当前会话的回传通道；无（如 cron 派活）则返回 null。 */
    public static AgentReplySink get() {
        return SINK.get();
    }

    public static void clear() {
        SINK.remove();
    }
}
