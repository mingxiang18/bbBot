package com.bb.bot.aiAgent.tools;

/**
 * 当前工具调用所属会话的「来源频道」上下文：平台（BotType 字符串）+ 群组 id。
 *
 * <p>背景：{@link MemoryToolContext} 只携带 caller userId，但有些工具（如定时任务的
 * cron_add）需要知道「这条消息是从哪个平台、哪个群发来的」，才能把任务目标落到当前频道。
 * 而 {@code @AiTool} 方法是 {@code AiToolExecutor} 通过反射调的，参数列表由 LLM 编排，
 * 群号不在签名里。</p>
 *
 * <p>解决：与 {@link MemoryToolContext} 同理，由 {@code AiToolExecutor} 在执行工具方法的
 * 那个线程上 set，finally 里 clear。非交互场景（如 cron 派活自身）groupId 可能为空，
 * 工具据此降级为私聊目标。</p>
 */
public final class ToolCallContext {

    private static final ThreadLocal<String> PLATFORM = new ThreadLocal<>();
    private static final ThreadLocal<String> GROUP_ID = new ThreadLocal<>();

    private ToolCallContext() {}

    public static void set(String platform, String groupId) {
        PLATFORM.set(platform);
        GROUP_ID.set(groupId);
    }

    /** 当前会话所在平台（BotType 字符串）；无则返回 null。 */
    public static String getPlatform() {
        return PLATFORM.get();
    }

    /** 当前会话的群组 id；私聊或非交互场景返回 null。 */
    public static String getGroupId() {
        return GROUP_ID.get();
    }

    public static void clear() {
        PLATFORM.remove();
        GROUP_ID.remove();
    }
}
