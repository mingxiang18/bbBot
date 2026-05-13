package com.bb.bot.aiAgent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动期扫描所有 Spring bean，把带 {@link AiTool} 注解的方法注册成可被 AI 调用的工具。
 *
 * <p>线程安全：扫描在 ContextRefreshed 时一次性完成，之后只读。</p>
 */
@Slf4j
@Component
public class AiToolRegistry {

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<String, AiToolDescriptor> tools = new LinkedHashMap<>();

    @EventListener
    public synchronized void onContextRefreshed(ContextRefreshedEvent event) {
        if (!tools.isEmpty()) {
            return;
        }
        Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);
        for (Object bean : beans.values()) {
            registerToolsFromBean(bean);
        }
        log.info("AiToolRegistry 注册完成，共 {} 个工具：{}", tools.size(), tools.keySet());
    }

    private void registerToolsFromBean(Object bean) {
        Class<?> clazz = org.springframework.aop.support.AopUtils.getTargetClass(bean);
        for (Method method : clazz.getDeclaredMethods()) {
            AiTool ann = method.getAnnotation(AiTool.class);
            if (ann == null) {
                continue;
            }
            method.setAccessible(true);
            List<AiToolDescriptor.ParamMeta> params = buildParamMetas(method);
            AiToolDescriptor desc = new AiToolDescriptor(
                    ann.name(),
                    ann.description(),
                    ann.requiresOwner(),
                    ann.requiresSandbox(),
                    bean,
                    method,
                    params);
            if (tools.containsKey(ann.name())) {
                throw new IllegalStateException("AiTool 名称冲突: " + ann.name() +
                        "（已注册于 " + tools.get(ann.name()).getMethod() +
                        "，当前 " + method + "）");
            }
            tools.put(ann.name(), desc);
        }
    }

    private List<AiToolDescriptor.ParamMeta> buildParamMetas(Method method) {
        List<AiToolDescriptor.ParamMeta> result = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        for (Parameter p : parameters) {
            AiToolParam ann = p.getAnnotation(AiToolParam.class);
            String name = (ann != null && !ann.name().isEmpty()) ? ann.name() : p.getName();
            String desc = ann != null ? ann.description() : "";
            boolean required = ann == null || ann.required();
            String jsonType = inferJsonType(p.getType());
            result.add(new AiToolDescriptor.ParamMeta(name, desc, required, jsonType, p.getType()));
        }
        return result;
    }

    private String inferJsonType(Class<?> javaType) {
        if (javaType == String.class) return "string";
        if (javaType == Boolean.class || javaType == boolean.class) return "boolean";
        if (javaType == Integer.class || javaType == int.class
                || javaType == Long.class || javaType == long.class
                || javaType == Short.class || javaType == short.class) return "integer";
        if (javaType == Double.class || javaType == double.class
                || javaType == Float.class || javaType == float.class) return "number";
        if (List.class.isAssignableFrom(javaType)) return "array";
        return "object";
    }

    public AiToolDescriptor get(String name) {
        return tools.get(name);
    }

    public Collection<AiToolDescriptor> all() {
        return tools.values();
    }

    /** 返回 OpenAI 协议 tools 字段值（每元素为 {type:function, function:{...}}）。 */
    public List<Object> toOpenAiTools() {
        List<Object> list = new ArrayList<>();
        for (AiToolDescriptor desc : tools.values()) {
            list.add(desc.toOpenAiSchema());
        }
        return list;
    }
}
