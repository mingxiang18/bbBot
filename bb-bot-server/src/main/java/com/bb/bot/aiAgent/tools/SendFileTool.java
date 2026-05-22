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
import java.util.Map;

/**
 * 把你文件空间里的一个文件作为附件发回当前对话的用户。
 *
 * <p>典型用法：用户上传文档 → 你用 shell_exec / file_write 处理生成产物 → 调本工具把
 * 产物发回去。文件必须在你自己的用户目录内（{@link AgentFileSpace} 越权校验）。</p>
 *
 * <ul>
 *   <li>{@code requiresOwner=true}：与 file_read/write、shell_exec 一致，仅 owner 可调</li>
 *   <li>出站通道经 {@link AgentReplyContext} 注入；非交互场景（cron）无通道 → 报错</li>
 *   <li>客户端未上报 file 能力时不硬发，返回提示让你改用文字告知</li>
 *   <li>上限 20MB（base64 走 WebSocket）</li>
 * </ul>
 */
@Slf4j
@Component
public class SendFileTool {

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @Autowired
    private AgentFileSpace fileSpace;

    @AiTool(
            name = "send_file",
            description = "把你文件空间里的一个文件作为附件发送给当前对话的用户。" +
                    "用户让你「把处理好的文件发给我 / 发我」时，先确保文件已在你的目录里" +
                    "（用 shell_exec 或 file_write 生成），再用本工具按相对路径发送。仅限你自己的目录，≤20MB。",
            requiresOwner = true
    )
    public Map<String, Object> send(
            @AiToolParam(name = "path", description = "要发送的文件路径（相对你的用户目录，或该目录内的绝对路径）")
            String path,
            @AiToolParam(name = "fileName", description = "展示给用户的文件名；不填则用文件本身的名字", required = false)
            String fileName
    ) {
        Map<String, Object> result = new LinkedHashMap<>();

        AgentReplySink sink = AgentReplyContext.get();
        if (sink == null) {
            result.put("error", "no_active_conversation");
            result.put("hint", "当前不在可回传的会话里（如定时任务），无法发送文件");
            return result;
        }

        Path target;
        try {
            target = fileSpace.resolveForCurrentUser(path);
        } catch (AgentFileSpace.PathEscapeException e) {
            result.put("error", "path_not_allowed");
            result.put("path", path);
            result.put("hint", "只能发送你自己用户目录里的文件");
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
                result.put("error", "file_too_large");
                result.put("size", size);
                result.put("maxSize", MAX_BYTES);
                return result;
            }
            if (!sink.fileSupported()) {
                result.put("error", "client_no_file_capability");
                result.put("hint", "对方客户端不支持接收文件，请改用文字把内容/链接告诉用户");
                return result;
            }
            String name = (fileName == null || fileName.isBlank())
                    ? target.getFileName().toString() : fileName.trim();
            sink.sendFile(target.toFile(), name);
            result.put("ok", true);
            result.put("fileName", name);
            result.put("size", size);
            return result;
        } catch (Exception e) {
            log.warn("send_file 失败 path={}", path, e);
            result.put("error", "send_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }
}
