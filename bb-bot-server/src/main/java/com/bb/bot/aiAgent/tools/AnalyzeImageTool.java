package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.fs.InboundImageStore;
import com.bb.bot.common.util.HashUtil;
import com.bb.bot.common.util.aiChat.provider.VisionDescriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 选择性识图工具：分析聊天记录 / 当前消息里某张图片，返回文字描述。
 *
 * <p>图片在上下文里以 {@code [图片 ref=<hash> 链接:/img/<hash>.png]} 形式出现，模型据情况
 * 自主决定是否调用本工具。先查两层缓存（内存 + DB image_vision_cache），未命中才调视觉模型，
 * 结果按内容哈希存库 —— 同一张图只识别一次。</p>
 *
 * @author ren
 */
@Slf4j
@Component
public class AnalyzeImageTool {

    @Autowired
    private InboundImageStore inboundImageStore;

    @Autowired
    private VisionDescriber visionDescriber;

    @AiTool(
            name = "analyze_image",
            description = "分析聊天记录或当前消息里的某一张图片，返回图中内容的文字描述。"
                    + "图片在上下文里标成 [图片 ref=xxx 链接:/img/xxx.png]，把那个 ref 传进来即可。"
                    + "只在你确实需要了解某张图内容时调用（按需、选择性）；结果会缓存，同图不会重复识别。",
            requiresOwner = false
    )
    public Map<String, Object> analyze(
            @AiToolParam(name = "image_ref", description = "图片旁标注的 ref（内容哈希）或其 /img/<hash>.png 链接")
            String imageRef
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (!visionDescriber.enabled()) {
            result.put("error", "vision_not_configured");
            result.put("hint", "管理员未配置视觉模型（ai.roles.vision），暂时无法识图");
            return result;
        }

        byte[] bytes = inboundImageStore.bytesForRef(imageRef);
        if (bytes == null || bytes.length == 0) {
            result.put("error", "image_not_found");
            result.put("image_ref", imageRef);
            result.put("hint", "没找到这个 ref 对应的图片，确认 ref 取自上下文里的 [图片 ref=...]");
            return result;
        }

        String hash = HashUtil.sha256Hex(bytes);
        String desc;
        try {
            desc = visionDescriber.describe(hash, bytes);
        } catch (Exception e) {
            log.warn("analyze_image 识别失败 ref={}", imageRef, e);
            result.put("error", "vision_failed");
            return result;
        }
        if (desc == null) {
            result.put("error", "vision_failed");
            result.put("hint", "视觉模型未返回描述");
            return result;
        }
        result.put("ref", hash);
        result.put("description", desc);
        return result;
    }
}
