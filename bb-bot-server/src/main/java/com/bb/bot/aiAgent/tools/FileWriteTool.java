package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用原语：把字符串写到一个本地文件（创建或覆盖 / 追加）。
 *
 * <p>参考 openhanako 的 PathGuard 思路简化实现：</p>
 * <ul>
 *   <li>{@code requiresOwner=true}：本工具能改宿主文件，默认仅 owner 角色</li>
 *   <li>路径必须落在 {@code aiAgent.fs.writeRoots} 配置（默认 {@code /tmp}）下，
 *       正规化后再校验，杜绝 {@code ../}</li>
 *   <li>单次写入 ≤ 256 KB，避免 LLM 失控写大文件</li>
 *   <li>{@code append=true} 走追加；否则覆盖（先 truncate 再写）</li>
 * </ul>
 */
@Slf4j
@Component
public class FileWriteTool {

    private static final int MAX_BYTES = 256 * 1024;

    @Autowired
    private FileReadTool fileReadTool;  // 复用 isAllowed 思路，但 write 用独立白名单

    @Value("${aiAgent.fs.writeRoots:/tmp}")
    private String writeRootsCsv;

    @AiTool(
            name = "file_write",
            description = "把文本内容写到本地一个文件（创建或覆盖；append=true 走追加）。" +
                    "仅允许 writeRoots 配置的目录下的路径（默认 /tmp）。" +
                    "用户让你「写一个文件 / 保存到 /xxx / 把这段写到 /yyy」时调用。" +
                    "单次 ≤ 256KB。",
            requiresOwner = true,
            requiresSandbox = false
    )
    public Map<String, Object> write(
            @AiToolParam(name = "path", description = "目标文件绝对路径")
            String path,
            @AiToolParam(name = "content", description = "要写入的文本内容")
            String content,
            @AiToolParam(name = "append", description = "true=追加，false=覆盖（默认）", required = false)
            Boolean append
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Path target = Paths.get(path).toAbsolutePath().normalize();
            if (!isAllowedWrite(target)) {
                result.put("error", "path_not_allowed");
                result.put("path", target.toString());
                result.put("allowedWriteRoots", writeRoots());
                return result;
            }
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
            if (parent != null && !java.nio.file.Files.exists(parent)) {
                java.nio.file.Files.createDirectories(parent);
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

    private List<String> writeRoots() {
        return Arrays.asList(writeRootsCsv.split(","));
    }

    private boolean isAllowedWrite(Path absoluteNormalized) {
        for (String root : writeRoots()) {
            Path rootPath = Paths.get(root.trim()).toAbsolutePath().normalize();
            if (absoluteNormalized.startsWith(rootPath)) return true;
        }
        return false;
    }
}
