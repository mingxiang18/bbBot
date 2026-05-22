package com.bb.bot.common.util.aiChat.provider;

/**
 * 单次模型调用的线程上下文：把「调用方是谁」（userId / platform / sessionId）和
 * 「本次调哪个 model / 属哪个层级」（modelOverride / modelRole）从上层 handler 一路带到
 * provider 层，供 token 用量归属（{@link TokenUsageRecorder}）与多模型路由（{@code resolveModel}）使用。
 *
 * <p>仿照 {@code MemoryToolContext}：调用链全程同步、同线程
 * （handler → {@link AiChatService} → {@link AbstractOpenAICompatibleProvider#chatStream}
 * 阻塞至 onComplete），所以 ThreadLocal 在 provider 层可见。</p>
 *
 * <p>身份（identity）由 handler 在入口 set / finally clear；模型层级（override/role）由
 * {@link AiChatService} 的 tier 重载在每次委托前后 set / clear，两组互不覆盖。</p>
 *
 * @author ren
 */
public final class AiCallContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> PLATFORM = new ThreadLocal<>();
    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> MODEL_ROLE = new ThreadLocal<>();

    private AiCallContext() {}

    // ---- 身份：handler set / clear ----

    public static void setIdentity(String userId, String platform, String sessionId) {
        USER_ID.set(userId);
        PLATFORM.set(platform);
        SESSION_ID.set(sessionId);
    }

    public static void clearIdentity() {
        USER_ID.remove();
        PLATFORM.remove();
        SESSION_ID.remove();
    }

    /** 无身份（后台 / 定时任务）时归到 _system。 */
    public static String userId() {
        String v = USER_ID.get();
        return v == null ? "_system" : v;
    }

    public static String platform() {
        return PLATFORM.get();
    }

    public static String sessionId() {
        return SESSION_ID.get();
    }

    // ---- 模型层级：AiChatService set / clear ----

    public static void setModelRole(String role) {
        MODEL_ROLE.set(role);
    }

    /** 缺省视为主对话层级 CHAT（ToolLoopExecutor 直连 provider、不经 tier 重载时）。 */
    public static String modelRole() {
        String v = MODEL_ROLE.get();
        return v == null ? ModelTier.CHAT.name() : v;
    }

    public static void clearModelRole() {
        MODEL_ROLE.remove();
    }
}
