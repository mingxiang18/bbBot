package com.bb.bot.aiAgent.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把一个方法标记为 AI 可调用工具。
 *
 * <p>方法所属类必须是 Spring bean。{@link AiToolRegistry} 会在启动时扫描所有
 * 带本注解的 bean 方法，把它们包成 OpenAI function calling 协议要求的 schema，
 * 注入到流式请求里。</p>
 *
 * <p>每个参数应该用 {@link AiToolParam} 标注，描述给 LLM 看。返回值会被
 * Jackson / fastjson2 序列化成字符串塞回 LLM 作为 tool message。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiTool {

    /** 工具名（function name）。在整个注册表中必须唯一。 */
    String name();

    /** 工具描述。LLM 看这段决定何时调用。建议 30-150 字符。 */
    String description();

    /** 是否需要 admin 角色（M4 授权阶段使用）。MVP 默认 false。 */
    boolean requiresOwner() default false;

    /** 是否走沙箱执行（M5 沙箱阶段使用）。MVP 默认 false。 */
    boolean requiresSandbox() default false;
}
