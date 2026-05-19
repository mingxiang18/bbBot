package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import com.bb.bot.aiAgent.fs.AgentFileSpace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 通用原语工具：列一个目录。
 *
 * <p>跟 {@link FileReadTool} 一样，路径经 {@link AgentFileSpace} 严格限定在
 * 当前调用者自己的用户目录内。</p>
 */
@Slf4j
@Component
public class ListDirTool {

    private static final int MAX_ENTRIES = 200;

    @Autowired
    private AgentFileSpace fileSpace;

    @AiTool(
            name = "list_dir",
            description = "列出一个目录下的文件 / 子目录（按名字排序，最多 200 条）。" +
                    "只能访问你自己的用户目录：路径可写相对路径（相对你的用户目录），" +
                    "或写绝对路径但必须落在该目录内。留空表示列你的用户目录根。" +
                    "用户让你「看一下 / 列出某个目录下有什么」时调用。"
    )
    public Map<String, Object> listDir(
            @AiToolParam(name = "path", description = "要列举的目录路径（相对你的用户目录，留空=用户目录根）", required = false)
            String path
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        Path dir;
        try {
            dir = fileSpace.resolveForCurrentUser(path);
        } catch (AgentFileSpace.PathEscapeException e) {
            result.put("error", "path_not_allowed");
            result.put("path", path);
            result.put("hint", "只能访问你自己的用户目录");
            return result;
        }
        try {
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
