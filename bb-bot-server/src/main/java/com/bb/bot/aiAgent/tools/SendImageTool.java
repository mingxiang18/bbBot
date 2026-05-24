package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.fs.AgentFileSpace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 把用户文件空间里的本地图片以内联图片形式发回当前对话。
 */
@Slf4j
@Component
public class SendImageTool {

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @Autowired
    private AgentFileSpace fileSpace;

    @AiTool(
            name = "send_image",
            description = "把你文件空间里的一个本地图片发送给当前对话的用户。" +
                    "当你用 shell_exec/Python 生成 png/jpg/webp/gif 图片后，必须调用本工具发送，" +
                    "不要把沙箱路径当 URL 回复，也不要编造网络图片链接。仅限你自己的目录，≤20MB。",
            requiresOwner = false
    )
    public Map<String, Object> send(
            @AiToolParam(name = "path", description = "要发送的图片路径（相对你的用户目录，或该目录内的绝对路径）")
            String path
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        AgentReplySink sink = AgentReplyContext.get();
        if (sink == null) {
            result.put("error", "no_active_conversation");
            result.put("hint", "当前不在可回传的会话里（如定时任务），无法发送图片");
            return result;
        }

        Path target;
        try {
            target = fileSpace.resolveForCurrentUser(path);
        } catch (AgentFileSpace.PathEscapeException e) {
            result.put("error", "path_not_allowed");
            result.put("path", path);
            result.put("hint", "只能发送你自己用户目录里的图片");
            return result;
        }

        if (!Files.exists(target) || Files.isDirectory(target)) {
            result.put("error", "file_not_found");
            result.put("path", target.toString());
            return result;
        }

        try {
            long size = Files.size(target);
            if (size > MAX_BYTES) {
                result.put("error", "image_too_large");
                result.put("size", size);
                result.put("maxSize", MAX_BYTES);
                return result;
            }
            if (!isImagePath(target)) {
                result.put("error", "not_supported_image");
                result.put("hint", "只支持 png/jpg/jpeg/webp/gif 图片；其它文件请用 send_file");
                return result;
            }
            if (!sink.imageSupported()) {
                result.put("error", "client_no_image_capability");
                result.put("hint", "对方客户端不支持接收图片，请改用文字告知或尝试 send_file");
                return result;
            }
            sink.sendImage(target.toFile());
            result.put("ok", true);
            result.put("fileName", target.getFileName().toString());
            result.put("size", size);
            return result;
        } catch (Exception e) {
            log.warn("send_image 失败 path={}", path, e);
            result.put("error", "send_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }

    private boolean isImagePath(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".webp")
                || name.endsWith(".gif");
    }
}
