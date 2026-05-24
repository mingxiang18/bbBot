package com.bb.bot.aiAgent.core;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 一个已注册工具的元信息。
 */
@Data
@AllArgsConstructor
public class AiToolDescriptor {

    private final String name;
    private final String description;
    private final boolean requiresOwner;
    private final boolean requiresSandbox;

    /** Spring bean 实例 + 反射目标方法。 */
    private final Object beanInstance;
    private final Method method;

    /** 参数描述（按方法签名顺序）。 */
    private final List<ParamMeta> params;

    @Data
    @AllArgsConstructor
    public static class ParamMeta {
        private final String name;
        private final String description;
        private final boolean required;
        /** JSON schema type："string" / "integer" / "number" / "boolean" / "object" / "array" */
        private final String jsonType;
        private final Class<?> javaType;
    }

    /**
     * 生成 OpenAI tools 字段所需的单个工具 JSON 结构（Map 形式，由 fastjson2 序列化）。
     */
    public Map<String, Object> toOpenAiSchema() {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        List<String> required = new java.util.ArrayList<>();
        for (ParamMeta p : params) {
            Map<String, Object> prop = new java.util.LinkedHashMap<>();
            prop.put("type", p.getJsonType());
            if (p.getDescription() != null && !p.getDescription().isEmpty()) {
                prop.put("description", p.getDescription());
            }
            properties.put(p.getName(), prop);
            if (p.isRequired()) {
                required.add(p.getName());
            }
        }
        Map<String, Object> parameters = new java.util.LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);

        Map<String, Object> function = new java.util.LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> wrapped = new java.util.LinkedHashMap<>();
        wrapped.put("type", "function");
        wrapped.put("function", function);
        return wrapped;
    }
}
