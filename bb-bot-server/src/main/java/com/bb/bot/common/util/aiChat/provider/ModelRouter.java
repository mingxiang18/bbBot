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
            "你是任务分类器。判断用户这条消息属于哪类，只回一个词，不要解释、不要标点：\n" +
            "- 闲聊、问候、简单问答、不需要执行任何操作 → 回 SIMPLE\n" +
            "- 需要执行操作（查实时/外部信息、读写文件、跑命令、联网搜索、写代码、多步骤任务）→ 回 COMPLEX";

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private AIProviderProperties properties;

    /**
     * 判断本轮该走轻模型(闲聊)还是重模型(干活)。
     * LIGHT → 闲聊，上层走轻模型且不挂工具；CHAT → 干活，上层走重模型 + 工具循环。
     */
    public ModelTier classify(List<ChatMessage> messages) {
        // light 角色没单独配（或与 heavy 同一个）→ 路由无意义，统一 CHAT
        AIProviderProperties.Roles roles = properties.getRoles();
        if (StringUtils.isBlank(roles.getLight()) || roles.getLight().equals(roles.getHeavy())) {
            return ModelTier.CHAT;
        }
        ChatMessage lastUser = lastUser(messages);
        if (lastUser == null) {
            return ModelTier.CHAT;
        }
        String text = textOf(lastUser);
        if (StringUtils.isBlank(text)) {
            // 纯图片无文字：当作轻量识图（视觉桥接处理），不挂工具
            return lastUser.hasImage() ? ModelTier.LIGHT : ModelTier.CHAT;
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
