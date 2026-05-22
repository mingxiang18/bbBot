package com.bb.bot.common.util.aiChat.provider;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 廉价模型分类器：用低级（LIGHT）模型判断本轮用户请求是轻量还是重度任务，
 * 决定走 {@link ModelTier#LIGHT} 还是 {@link ModelTier#CHAT}。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>LIGHT 层级未配置 → 路由无意义，直接 CHAT（不浪费分类调用）</li>
 *   <li>含图片 → CHAT（交给视觉桥接 / 重模型）</li>
 *   <li>否则用 LIGHT 模型快速分类，SIMPLE → LIGHT，其余 / 失败 → CHAT（保证质量）</li>
 * </ul>
 *
 * @author ren
 */
@Slf4j
@Component
public class ModelRouter {

    private static final String CLASSIFY_PROMPT =
            "你是任务分类器。判断下面这条用户请求属于哪类，只回一个词，不要解释、不要标点：\n" +
            "- 简单闲聊、问候、简单事实问答 → 回 SIMPLE\n" +
            "- 需要复杂推理、多步骤、写代码、长文创作、专业分析 → 回 COMPLEX";

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private AIProviderProperties properties;

    public ModelTier classify(List<ChatMessage> messages) {
        if (!properties.getTiers().getLight().isConfigured()) {
            return ModelTier.CHAT;
        }
        ChatMessage lastUser = lastUser(messages);
        if (lastUser == null || lastUser.hasImage()) {
            return ModelTier.CHAT;
        }
        String text = textOf(lastUser);
        if (StringUtils.isBlank(text)) {
            return ModelTier.CHAT;
        }
        try {
            List<ChatMessage> req = List.of(
                    ChatMessage.system(CLASSIFY_PROMPT),
                    ChatMessage.user(text));
            String verdict = aiChatService.chat(req, ModelTier.LIGHT);
            if (verdict != null && verdict.toUpperCase().contains("SIMPLE")) {
                log.debug("ModelRouter → LIGHT: {}", abbreviate(text));
                return ModelTier.LIGHT;
            }
        } catch (Exception e) {
            log.warn("ModelRouter 分类失败，回退 CHAT", e);
        }
        return ModelTier.CHAT;
    }

    private ChatMessage lastUser(List<ChatMessage> messages) {
        if (messages == null) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getRole() == ChatMessage.Role.USER) {
                return messages.get(i);
            }
        }
        return null;
    }

    private String textOf(ChatMessage m) {
        StringBuilder sb = new StringBuilder();
        for (MessageContent c : m.getContents()) {
            if (c.getType() == MessageContent.Type.TEXT && c.getValue() != null) {
                sb.append(c.getValue());
            }
        }
        return sb.toString().trim();
    }

    private String abbreviate(String s) {
        return s.length() <= 40 ? s : s.substring(0, 40) + "…";
    }
}
