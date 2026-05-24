package com.bb.bot.aiAgent.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 {@link AiTool} 方法的参数，生成 OpenAI function calling 的 JSON Schema。
 *
 * <p>未标注的参数会按 Java 类型推断 type，但 description / required 默认为合理值。</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiToolParam {

    /** 参数名（function calling JSON 的字段名）。空字符串则用 Java 反射的参数名。 */
    String name() default "";

    /** 给 LLM 看的描述。 */
    String description() default "";

    /** 是否必填。默认 true。 */
    boolean required() default true;
}
