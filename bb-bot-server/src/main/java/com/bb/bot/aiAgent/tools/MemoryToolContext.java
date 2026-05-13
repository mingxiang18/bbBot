package com.bb.bot.aiAgent.tools;

/**
 * Memory 工具的调用方上下文（caller user id）。
 *
 * <p>背景：MemoryViewTool / SearchMemoryTool / RecallExperienceTool 等需要知道
 * 当前 caller 是谁，从而隔离 user namespace。AiToolExecutor 调具体 @AiTool 方法是
 * 通过反射，参数列表是 LLM 编排的，user_id 不在签名里。</p>
 *
 * <p>解决：由 AiToolExecutor 在 invoke 入口处把 user_id 放进 ThreadLocal；
 * memory 系列工具用本类的 getUserId() 取。BbAiAgentHandler 等 caller 来自
 * 同一线程 → ThreadLocal 安全。AiToolExecutor 的 toolPool 是 cachedThreadPool，
 * 任务跑完会被 finally 块清掉。</p>
 */
public final class MemoryToolContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private MemoryToolContext() {}

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static String getUserId() {
        String id = USER_ID.get();
        return id == null ? "_anonymous" : id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
