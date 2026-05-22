package com.bb.bot.common.util.aiChat.provider;

import com.bb.bot.database.aiAgent.entity.AiTokenUsage;
import com.bb.bot.database.aiAgent.service.IAiTokenUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 把 provider 解析到的 token 用量异步落库。归属信息（userId / platform / sessionId / role）
 * 从 {@link AiCallContext} 取——provider 是唯一能拿到原始 usage 的地方，调用方身份
 * 在同线程的 ThreadLocal 里。
 *
 * @author ren
 */
@Slf4j
@Component
public class TokenUsageRecorder {

    @Autowired
    private IAiTokenUsageService tokenUsageService;

    /** 单线程异步落库，不阻塞模型调用线程；满了直接由调用线程兜底执行。 */
    private final ExecutorService writePool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-token-usage-writer");
        t.setDaemon(true);
        return t;
    });

    /**
     * @param providerName provider 名（openai / deepseek）
     * @param model        实际使用的 model
     * @param prompt       prompt token 数
     * @param completion   completion token 数
     * @param total        total token 数（部分网关只给 total）
     */
    public void record(String providerName, String model, int prompt, int completion, int total) {
        // 在调用线程先抓取 ThreadLocal 身份，再丢给异步线程写库
        final String userId = AiCallContext.userId();
        final String platform = AiCallContext.platform();
        final String sessionId = AiCallContext.sessionId();
        final String role = AiCallContext.modelRole();
        try {
            writePool.execute(() -> persist(userId, platform, sessionId, role,
                    providerName, model, prompt, completion, total));
        } catch (Exception e) {
            log.warn("token 用量入队失败（忽略，不影响对话）", e);
        }
    }

    private void persist(String userId, String platform, String sessionId, String role,
                         String providerName, String model, int prompt, int completion, int total) {
        try {
            AiTokenUsage row = new AiTokenUsage();
            row.setUserId(userId);
            row.setPlatform(platform);
            row.setSessionId(sessionId);
            row.setModelRole(role);
            row.setProviderName(providerName);
            row.setModel(model);
            row.setPromptTokens(prompt);
            row.setCompletionTokens(completion);
            row.setTotalTokens(total);
            row.setCreatedAt(LocalDateTime.now());
            tokenUsageService.save(row);
        } catch (Exception e) {
            log.warn("token 用量落库失败 user={} model={}", userId, model, e);
        }
    }
}
