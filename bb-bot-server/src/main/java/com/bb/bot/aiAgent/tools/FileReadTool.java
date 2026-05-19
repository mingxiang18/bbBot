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
 * 通用原语工具：读一个本地文件的文本内容。
 *
 * <p>安全护栏：</p>
 * <ul>
 *   <li>路径经 {@link AgentFileSpace} 严格限定在「当前调用者自己的用户目录」内，
 *       相对路径相对该目录解析，绝对路径必须落在该目录子树下，杜绝越权与 {@code ../} 越狱</li>
 *   <li>单次最多读 64 KB 字符，超出截断</li>
 *   <li>requiresOwner=false（只读 + 每用户隔离已经够安全）</li>
 * </ul>
 */
@Slf4j
@Component
public class FileReadTool {

    private static final int MAX_CHARS = 64 * 1024;

    @Autowired
    private AgentFileSpace fileSpace;

    @AiTool(
            name = "file_read",
            description = "读取一个本地文本文件的内容。" +
                    "只能访问你自己的用户目录：路径可写相对路径（相对你的用户目录），" +
                    "或写绝对路径但必须落在该目录内，否则拒绝。" +
                    "用户让你「看一下 / 读一下 / 打开某个文件」、或聊天里给了附件文件路径时调用本工具。" +
                    "返回 path、size、content（>64KB 自动截断）。"
    )
    public Map<String, Object> read(
            @AiToolParam(name = "path", description = "要读取的文件路径（相对你的用户目录，或该目录内的绝对路径）")
            String path
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        Path target;
        try {
            target = fileSpace.resolveForCurrentUser(path);
        } catch (AgentFileSpace.PathEscapeException e) {
            result.put("error", "path_not_allowed");
            result.put("path", path);
            result.put("hint", "只能访问你自己的用户目录");
            return result;
        }
        try {
            if (!Files.exists(target)) {
                result.put("error", "not_found");
                result.put("path", target.toString());
                return result;
            }
            if (Files.isDirectory(target)) {
                result.put("error", "is_directory");
                result.put("path", target.toString());
                result.put("hint", "用 list_dir 工具查目录");
                return result;
            }
            long size = Files.size(target);
            String content = Files.readString(target);
            boolean truncated = content.length() > MAX_CHARS;
            if (truncated) {
                content = content.substring(0, MAX_CHARS) + "...[truncated]";
            }
            result.put("path", target.toString());
            result.put("size", size);
            result.put("truncated", truncated);
            result.put("content", content);
            return result;
        } catch (Exception e) {
            log.warn("file_read 失败 path={}", path, e);
            result.put("error", "read_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }
}
