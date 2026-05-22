package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.fs.AgentFileSpace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用原语：把字符串写到一个本地文件（创建或覆盖 / 追加）。
 *
 * <ul>
 *   <li>{@code requiresOwner=true}：默认仅 owner 角色</li>
 *   <li>路径经 {@link AgentFileSpace} 严格限定在「当前调用者自己的用户目录」内，
 *       正规化后再校验，杜绝越权与 {@code ../}</li>
 *   <li>单次写入 ≤ 256 KB，避免 LLM 失控写大文件</li>
 *   <li>{@code append=true} 走追加；否则覆盖（先 truncate 再写）</li>
 * </ul>
 */
@Slf4j
@Component
public class FileWriteTool {

    private static final int MAX_BYTES = 256 * 1024;

    @Autowired
    private AgentFileSpace fileSpace;

    @AiTool(
            name = "file_write",
            description = "把文本内容写到一个本地文件（创建或覆盖；append=true 走追加）。" +
                    "只能写到你自己的用户目录：路径可写相对路径（相对你的用户目录），" +
                    "或写绝对路径但必须落在该目录内，否则拒绝。" +
                    "用户让你「写一个文件 / 保存这段内容」时调用。单次 ≤ 256KB。",
            requiresOwner = true,
            requiresSandbox = false
    )
    public Map<String, Object> write(
            @AiToolParam(name = "path", description = "目标文件路径（相对你的用户目录，或该目录内的绝对路径）")
            String path,
            @AiToolParam(name = "content", description = "要写入的文本内容")
            String content,
            @AiToolParam(name = "append", description = "true=追加，false=覆盖（默认）", required = false)
            Boolean append
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 确保用户根目录 0777：与 bb-sandbox(uid 1000) 共享 hostPath，双方都要能写
        fileSpace.ensureSharedUserDir(MemoryToolContext.getUserId());
        Path target;
        try {
            target = fileSpace.resolveForCurrentUser(path);
        } catch (AgentFileSpace.PathEscapeException e) {
            result.put("error", "path_not_allowed");
            result.put("path", path);
            result.put("hint", "只能写到你自己的用户目录");
            return result;
        }
        try {
            if (content == null) content = "";
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) {
                result.put("error", "content_too_large");
                result.put("size", bytes.length);
                result.put("maxSize", MAX_BYTES);
                return result;
            }
            // 父目录如不存在则建
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (Boolean.TRUE.equals(append)) {
                Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            result.put("path", target.toString());
            result.put("bytesWritten", bytes.length);
            result.put("mode", Boolean.TRUE.equals(append) ? "append" : "overwrite");
            return result;
        } catch (Exception e) {
            log.warn("file_write 失败 path={}", path, e);
            result.put("error", "write_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }
}
