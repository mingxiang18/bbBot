package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用原语工具：读一个本地文件的文本内容。
 *
 * <p>安全护栏：</p>
 * <ul>
 *   <li>仅允许从 {@code aiAgent.fs.allowedRoots} 配置的目录前缀下读，默认 {@code /tmp}</li>
 *   <li>正规化后再校验，杜绝 {@code ../} 越狱</li>
 *   <li>单次最多读 64 KB 字符，超出截断</li>
 *   <li>requiresOwner=false（只读 + 路径白名单已经够安全）</li>
 * </ul>
 */
@Slf4j
@Component
public class FileReadTool {

    private static final int MAX_CHARS = 64 * 1024;

    @Value("${aiAgent.fs.allowedRoots:/tmp}")
    private String allowedRootsCsv;

    @AiTool(
            name = "file_read",
            description = "读取本地一个文本文件的内容。" +
                    "仅允许 allowedRoots 配置的目录下的文件（默认 /tmp）。" +
                    "用户让你「看一下 / 读一下 / 打开 /xxx 文件」时调用本工具。" +
                    "返回 path、size、content（>64KB 自动截断）。"
    )
    public Map<String, Object> read(
            @AiToolParam(name = "path", description = "要读取的绝对路径")
            String path
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Path target = Paths.get(path).toAbsolutePath().normalize();
            if (!isAllowed(target)) {
                result.put("error", "path_not_allowed");
                result.put("path", target.toString());
                result.put("allowedRoots", allowedRoots());
                return result;
            }
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

    private List<String> allowedRoots() {
        return Arrays.asList(allowedRootsCsv.split(","));
    }

    boolean isAllowed(Path absoluteNormalized) {
        for (String root : allowedRoots()) {
            Path rootPath = Paths.get(root.trim()).toAbsolutePath().normalize();
            if (absoluteNormalized.startsWith(rootPath)) {
                return true;
            }
        }
        return false;
    }
}
