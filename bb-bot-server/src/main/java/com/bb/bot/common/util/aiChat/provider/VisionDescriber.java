package com.bb.bot.common.util.aiChat.provider;

import com.bb.bot.database.aiAgent.service.IImageVisionCacheService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 按内容哈希描述图片，并做两层缓存（内存 Caffeine + DB image_vision_cache）。
 *
 * <p>供 {@code AnalyzeImageTool} 复用：同一张图（同 sha256(bytes)）只调一次视觉模型，
 * 描述跨重启 / 多实例复用。未配置 {@code ai.roles.vision} 时 {@link #describe} 返回 null，
 * 调用方据此降级（不报错刷屏）。</p>
 *
 * @author ren
 */
@Slf4j
@Component
public class VisionDescriber {

    /** 与 VisionBridge 一致的识图提示。 */
    public static final String VISION_PROMPT =
            "详细描述这张图片的内容。对每个可识别的物体、文字、UI 元素，说明它在画面中的方位"
            + "（如左上、正中、右下等）以及相互之间的位置关系。用纯中文文字输出，不要使用数字坐标，"
            + "不要寒暄，直接给描述。";

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private IImageVisionCacheService cacheService;

    /** 图片 hash → 描述。和 DB 共同构成两层缓存。 */
    private final Cache<String, String> memCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(6))
            .build();

    /** 视觉模型是否已配置（ai.roles.vision）。 */
    public boolean enabled() {
        return aiChatService.visionConfigured();
    }

    /**
     * 按内容哈希取（或生成并缓存）图片描述。
     *
     * @param hash  sha256(bytes) 的 hex（{@link com.bb.bot.common.util.HashUtil#sha256Hex}）
     * @param bytes 图片原始字节
     * @return 描述文本；视觉未配置或识别失败返回 null
     */
    public String describe(String hash, byte[] bytes) {
        if (StringUtils.isBlank(hash) || bytes == null || bytes.length == 0) {
            return null;
        }
        String mem = memCache.getIfPresent(hash);
        if (mem != null) {
            return mem;
        }
        Optional<String> db = cacheService.findDescription(hash);
        if (db.isPresent()) {
            memCache.put(hash, db.get());
            return db.get();
        }
        if (!aiChatService.visionConfigured()) {
            return null;
        }
        String desc;
        String model = null;
        try {
            ModelSpec spec = aiChatService.specForTier(ModelTier.VISION);
            model = spec == null ? null : spec.getModel();
            String dataUrl = "data:" + sniffMime(bytes) + ";base64,"
                    + Base64.getEncoder().encodeToString(bytes);
            List<ChatMessage> req = List.of(
                    ChatMessage.system(VISION_PROMPT),
                    ChatMessage.user(List.of(MessageContent.base64Image(dataUrl))));
            String result = aiChatService.chat(req, ModelTier.VISION);
            desc = StringUtils.isNotBlank(result) ? result.trim() : null;
        } catch (Exception e) {
            log.warn("视觉模型识别图片失败 hash={}", hash, e);
            return null;
        }
        if (desc == null) {
            return null;
        }
        memCache.put(hash, desc);
        cacheService.put(hash, desc, model);
        return desc;
    }

    private String sniffMime(byte[] b) {
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (b.length >= 4 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') {
            return "image/gif";
        }
        if (b.length >= 12 && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return "image/png";
    }
}
