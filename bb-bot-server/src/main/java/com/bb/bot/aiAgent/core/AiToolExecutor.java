package com.bb.bot.aiAgent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.aiAgent.auth.AiAgentAuthService;
import com.bb.bot.database.aiAgent.entity.AiToolInvocationLog;
import com.bb.bot.database.aiAgent.service.IAiToolInvocationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
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

    @Autowired
    private AiAgentAuthService authService;

    @Autowired
    private IAiToolInvocationLogService invocationLogService;

    private final ExecutorService toolPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-tool-" + System.nanoTime());
        t.setDaemon(true);
        return t;
    });

    /** 单次工具执行的硬超时。 */
    private static final long TOOL_TIMEOUT_SECONDS = 30;

    /**
     * 兼容旧调用签名（无 platform / sessionId）。建议改用 4 参数版本。
     */
    public String invoke(String toolName, String argsJson, String callerUserId) {
        return invoke(toolName, argsJson, callerUserId, null, null);
    }

    /**
     * 执行一次工具调用。
     *
     * <p>调用顺序：① auth 预检 → ② 参数解析 → ③ 沙箱 / 反射执行 → ④ 审计日志落库</p>
     *
     * @param toolName     工具名
     * @param argsJson     function calling arguments
     * @param callerUserId 调用者 user id
     * @param platform     平台标识（BotType 字符串），用于角色查询和审计
     * @param sessionId    一次 agent 派活的串联 id（null 也行，落库时为空）
     */
    public String invoke(String toolName, String argsJson, String callerUserId, String platform, String sessionId) {
        long start = System.currentTimeMillis();
        AiToolDescriptor desc = registry.get(toolName);
        if (desc == null) {
            String err = "{\"error\":\"unknown_tool\",\"tool\":\"" + toolName + "\"}";
            audit(sessionId, callerUserId, platform, toolName, argsJson, err, start, "error");
            return err;
        }

        AiAgentAuthService.AuthDecision decision = authService.canInvoke(callerUserId, platform, toolName);
        if (!decision.allowed) {
            log.warn("授权拒绝：user={} platform={} tool={} reason={}", callerUserId, platform, toolName, decision.reason);
            String err = "{\"error\":\"permission_denied\",\"tool\":\"" + toolName + "\",\"reason\":\"" + decision.reason + "\"}";
            audit(sessionId, callerUserId, platform, toolName, argsJson, err, start, "denied");
            return err;
        }

        Object[] args = parseArgs(desc, argsJson);
        if (args == null) {
            String err = "{\"error\":\"invalid_arguments\",\"tool\":\"" + toolName + "\",\"raw\":" + JSON.toJSONString(argsJson) + "}";
            audit(sessionId, callerUserId, platform, toolName, argsJson, err, start, "error");
            return err;
        }

        // 把 caller user_id 放进 ThreadLocal，memory 系列工具用它做 namespace 隔离
        Callable<Object> task = () -> {
            com.bb.bot.aiAgent.tools.MemoryToolContext.setUserId(callerUserId);
            try {
                return desc.getMethod().invoke(desc.getBeanInstance(), args);
            } finally {
                com.bb.bot.aiAgent.tools.MemoryToolContext.clear();
            }
        };
        Future<Object> future = toolPool.submit(task);
        try {
            Object result = future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String text = serializeResult(result);
            audit(sessionId, callerUserId, platform, toolName, argsJson, text, start, "ok");
            return text;
        } catch (TimeoutException toe) {
            future.cancel(true);
            log.warn("工具 {} 执行超时", toolName);
            String err = "{\"error\":\"timeout\",\"tool\":\"" + toolName + "\",\"timeoutSec\":" + TOOL_TIMEOUT_SECONDS + "}";
            audit(sessionId, callerUserId, platform, toolName, argsJson, err, start, "timeout");
            return err;
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
            log.warn("工具 {} 执行失败", toolName, cause);
            String err = "{\"error\":\"execution_failed\",\"tool\":\"" + toolName + "\",\"message\":" + JSON.toJSONString(cause == null ? "" : cause.getMessage()) + "}";
            audit(sessionId, callerUserId, platform, toolName, argsJson, err, start, "error");
            return err;
        }
    }

    private void audit(String sessionId, String userId, String platform, String toolName,
                       String argsJson, String resultJson, long startMs, String status) {
        try {
            AiToolInvocationLog row = new AiToolInvocationLog();
            row.setSessionId(sessionId);
            row.setUserId(userId);
            row.setPlatform(platform);
            row.setToolName(toolName);
            row.setArgsJson(argsJson);
            // 审计 result 限长，避免把 4KB 抓取正文塞满表
            row.setResultJson(resultJson != null && resultJson.length() > 2000
                    ? resultJson.substring(0, 2000) + "...[truncated]"
                    : resultJson);
            row.setLatencyMs(System.currentTimeMillis() - startMs);
            row.setStatus(status);
            row.setCreatedAt(LocalDateTime.now());
            invocationLogService.save(row);
        } catch (Exception e) {
            log.warn("ai_tool_invocation_log 落库失败（非致命）", e);
        }
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
