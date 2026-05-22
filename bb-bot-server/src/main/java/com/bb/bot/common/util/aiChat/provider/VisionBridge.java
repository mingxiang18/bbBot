package com.bb.bot.common.util.aiChat.provider;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 视觉桥接（参考 openhanako vision-bridge）：主模型不支持视觉但消息里带图时，
 * 把图片交给配置好的 {@link ModelTier#VISION} 模型生成「含方位的文字描述」，
 * 再用该描述文本替换上下文里的图片 part，喂给主（纯文本）模型。
 *
 * <p>插在 {@link AiChatService} 委托主 provider 之前，对整条消息列表处理一次；
 * provider 内部原有的 stripImages 退化为兜底（届时已无图片）。</p>
 *
 * <p>触发条件全满足才桥接：主 provider 无视觉 + 消息含图 + VISION 层级已配置且就绪；
 * 否则原样返回（行为与未启用时一致）。描述按图片 hash 缓存，避免重试 / 多轮历史重复识别。</p>
 *
 * @author ren
 */
@Slf4j
@Component
public class VisionBridge {

    private static final String VISION_PROMPT =
            "详细描述这张图片的内容。对每个可识别的物体、文字、UI 元素，说明它在画面中的方位"
            + "（如左上、正中、右下等）以及相互之间的位置关系。用纯中文文字输出，不要使用数字坐标，"
            + "不要寒暄，直接给描述。";

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private AIProviderProperties properties;

    /** 图片 hash → 描述文本。避免同一张图重复调用视觉模型。 */
    private final Cache<String, String> descriptionCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(6))
            .build();

    /**
     * 按需桥接。不满足触发条件时原样返回入参列表（不复制）。
     */
    public List<ChatMessage> bridgeIfNeeded(List<ChatMessage> messages, AIProvider mainProvider) {
        if (mainProvider == null || mainProvider.visionEnable()) {
            return messages; // 主模型直接看图，无需桥接
        }
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        boolean hasImage = messages.stream().anyMatch(ChatMessage::hasImage);
        if (!hasImage) {
            return messages;
        }
        if (!properties.getTiers().getVision().isConfigured()) {
            return messages; // 没配视觉模型 → provider 照旧 stripImages
        }
        AIProvider visionProvider = aiChatService.resolveProvider(ModelTier.VISION);
        if (visionProvider == null || !visionProvider.isConfigured() || !visionProvider.visionEnable()) {
            log.warn("VISION 层级未就绪（provider={}），跳过视觉桥接",
                    visionProvider == null ? null : visionProvider.name());
            return messages;
        }

        List<ChatMessage> out = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            out.add(m.hasImage() ? replaceImagesWithDescription(m) : m);
        }
        return out;
    }

    /** 把一条消息里的图片 part 换成视觉模型生成的文字描述，保留其余字段。 */
    private ChatMessage replaceImagesWithDescription(ChatMessage m) {
        List<MessageContent> newParts = new ArrayList<>(m.getContents().size());
        for (MessageContent part : m.getContents()) {
            if (part.isImage()) {
                String desc = describe(part);
                newParts.add(MessageContent.text("[图片描述] " + desc));
            } else {
                newParts.add(part);
            }
        }
        ChatMessage copy = new ChatMessage(m.getRole(), newParts);
        copy.setToolCalls(m.getToolCalls());
        copy.setToolCallId(m.getToolCallId());
        copy.setReasoningContent(m.getReasoningContent());
        return copy;
    }

    /** 取（或生成并缓存）单张图片的方位文字描述。失败时降级为占位提示。 */
    private String describe(MessageContent imagePart) {
        String key = cacheKey(imagePart);
        String cached = descriptionCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        String desc;
        try {
            List<ChatMessage> req = List.of(
                    ChatMessage.system(VISION_PROMPT),
                    ChatMessage.user(List.of(imagePart)));
            String result = aiChatService.chat(req, ModelTier.VISION);
            desc = StringUtils.isNotBlank(result) ? result.trim() : "（视觉模型未返回描述）";
        } catch (Exception e) {
            log.warn("视觉模型识别图片失败，降级为占位描述", e);
            desc = "（图片识别失败，无法提供描述）";
        }
        descriptionCache.put(key, desc);
        return desc;
    }

    private String cacheKey(MessageContent imagePart) {
        return imagePart.getType().name() + ":" + sha256(imagePart.getValue());
    }

    private String sha256(String s) {
        if (s == null) {
            return "null";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 一定存在；兜底用 hashCode
            return Integer.toHexString(s.hashCode());
        }
    }
}
