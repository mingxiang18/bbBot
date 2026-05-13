package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 通用原语工具：列一个目录。
 *
 * <p>跟 {@link FileReadTool} 共用路径白名单（避免两套配置）。</p>
 */
@Slf4j
@Component
public class ListDirTool {

    private static final int MAX_ENTRIES = 200;

    @Autowired
    private FileReadTool fileReadTool;  // 复用 isAllowed

    @AiTool(
            name = "list_dir",
            description = "列出一个目录下的文件 / 子目录（按名字排序，最多 200 条）。" +
                    "仅允许 allowedRoots 配置的目录下（默认 /tmp）。" +
                    "用户让你「看一下 / 列出 /xxx 下有什么」时调用。"
    )
    public Map<String, Object> listDir(
            @AiToolParam(name = "path", description = "要列举的目录绝对路径")
            String path
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Path dir = Paths.get(path).toAbsolutePath().normalize();
            if (!fileReadTool.isAllowed(dir)) {
                result.put("error", "path_not_allowed");
                result.put("path", dir.toString());
                return result;
            }
            if (!Files.exists(dir)) {
                result.put("error", "not_found");
                result.put("path", dir.toString());
                return result;
            }
            if (!Files.isDirectory(dir)) {
                result.put("error", "not_a_directory");
                result.put("path", dir.toString());
                return result;
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            try (Stream<Path> children = Files.list(dir)) {
                children.sorted().limit(MAX_ENTRIES).forEach(p -> {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("name", p.getFileName().toString());
                    e.put("type", Files.isDirectory(p) ? "dir" : "file");
                    try {
                        e.put("size", Files.isDirectory(p) ? null : Files.size(p));
                    } catch (Exception ignore) {
                        e.put("size", null);
                    }
                    entries.add(e);
                });
            }
            result.put("path", dir.toString());
            result.put("count", entries.size());
            result.put("entries", entries);
            return result;
        } catch (Exception e) {
            log.warn("list_dir 失败 path={}", path, e);
            result.put("error", "list_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }
}
