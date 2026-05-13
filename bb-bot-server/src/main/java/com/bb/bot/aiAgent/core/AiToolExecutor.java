package com.bb.bot.aiAgent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 把 LLM 发来的 tool_call (name + arguments JSON 字符串) 解析、反射调用、
 * 把结果序列化成可回灌的字符串。
 *
 * <p>每个工具调用单独超时（默认 30s）。失败 / 超时 / 拒绝（权限不足）的情况都
 * 返回一个用户态错误字符串塞回 LLM 让它自我修正或换工具。</p>
 */
@Slf4j
@Component
public class AiToolExecutor {

    @Autowired
    private AiToolRegistry registry;

    private final ExecutorService toolPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-tool-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    /** 单次工具执行的硬超时。 */
    private static final long TOOL_TIMEOUT_SECONDS = 30;

    /**
     * 执行一次工具调用。
     *
     * @param toolName  工具名（function.name）
     * @param argsJson  function calling 的 arguments 字段（JSON 字符串）
     * @param callerUserId 调用者用户 ID（M4 授权使用，MVP 阶段透传备用）
     * @return 序列化后的字符串结果（成功时是 JSON 或文本，失败时是错误消息）
     */
    public String invoke(String toolName, String argsJson, String callerUserId) {
        AiToolDescriptor desc = registry.get(toolName);
        if (desc == null) {
            return "{\"error\":\"unknown_tool\",\"tool\":\"" + toolName + "\"}";
        }

        // 权限预检（MVP 阶段，仅按 requiresOwner 标志做简单 owner 校验；M4 替换为 DB 策略）
        if (desc.isRequiresOwner() && !isOwner(callerUserId)) {
            log.warn("非 owner 用户 {} 试图调用需要 owner 权限的工具 {}", callerUserId, toolName);
            return "{\"error\":\"permission_denied\",\"tool\":\"" + toolName + "\"}";
        }

        Object[] args = parseArgs(desc, argsJson);
        if (args == null) {
            return "{\"error\":\"invalid_arguments\",\"tool\":\"" + toolName + "\",\"raw\":" + JSON.toJSONString(argsJson) + "}";
        }

        Callable<Object> task = () -> desc.getMethod().invoke(desc.getBeanInstance(), args);
        Future<Object> future = toolPool.submit(task);
        try {
            Object result = future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return serializeResult(result);
        } catch (TimeoutException toe) {
            future.cancel(true);
            log.warn("工具 {} 执行超时", toolName);
            return "{\"error\":\"timeout\",\"tool\":\"" + toolName + "\",\"timeoutSec\":" + TOOL_TIMEOUT_SECONDS + "}";
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
            log.warn("工具 {} 执行失败", toolName, cause);
            return "{\"error\":\"execution_failed\",\"tool\":\"" + toolName + "\",\"message\":" + JSON.toJSONString(cause == null ? "" : cause.getMessage()) + "}";
        }
    }

    /** MVP owner 判断：硬编码原 BbAiChatHandler 用的 UID。M4 接 MySQL 角色表后替换。 */
    private boolean isOwner(String userId) {
        return "1105048721".equals(userId);
    }

    private Object[] parseArgs(AiToolDescriptor desc, String argsJson) {
        try {
            JSONObject obj = argsJson == null || argsJson.isEmpty() ? new JSONObject() : JSON.parseObject(argsJson);
            Object[] result = new Object[desc.getParams().size()];
            int i = 0;
            for (AiToolDescriptor.ParamMeta p : desc.getParams()) {
                Object raw = obj.get(p.getName());
                if (raw == null) {
                    if (p.isRequired()) {
                        log.warn("工具 {} 缺少必填参数 {}", desc.getName(), p.getName());
                        return null;
                    }
                    result[i++] = defaultFor(p.getJavaType());
                    continue;
                }
                result[i++] = JSON.parseObject(JSON.toJSONString(raw), p.getJavaType());
            }
            return result;
        } catch (Exception e) {
            log.warn("解析工具 {} 参数失败: {}", desc.getName(), argsJson, e);
            return null;
        }
    }

    private Object defaultFor(Class<?> javaType) {
        if (javaType == int.class || javaType == long.class) return 0;
        if (javaType == boolean.class) return false;
        if (javaType == double.class || javaType == float.class) return 0.0;
        return null;
    }

    private String serializeResult(Object result) {
        if (result == null) {
            return "{\"ok\":true}";
        }
        if (result instanceof String s) {
            return s;
        }
        return JSON.toJSONString(result);
    }
}
