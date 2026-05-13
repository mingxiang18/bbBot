package com.bb.bot.aiAgent.tools;

import com.bb.bot.aiAgent.core.AiTool;
import com.bb.bot.aiAgent.core.AiToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 通用原语：在白名单目录树内做内容搜索（递归 + 正则）。
 *
 * <p>护栏：</p>
 * <ul>
 *   <li>路径必须在 {@code FileReadTool.allowedRoots} 之内（共用配置）</li>
 *   <li>单文件 > 1MB 直接跳过</li>
 *   <li>结果上限 50 条</li>
 *   <li>仅扫文本文件（按扩展名简单过滤；二进制跳过）</li>
 * </ul>
 */
@Slf4j
@Component
public class GrepSearchTool {

    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final List<String> TEXT_EXTS = List.of(
            ".txt", ".md", ".log", ".json", ".yml", ".yaml", ".xml", ".html", ".htm",
            ".java", ".js", ".mjs", ".ts", ".tsx", ".py", ".rb", ".go", ".rs",
            ".sh", ".bash", ".css", ".scss", ".sql", ".csv", ".properties", ".conf"
    );

    @Autowired
    private FileReadTool fileReadTool;  // 共用 isAllowed

    @AiTool(
            name = "grep_search",
            description = "在白名单目录树里搜内容（正则 + 递归）。" +
                    "用户让你「找一下哪个文件提到 X / 包含 X 的文件 / 搜 X」时用。" +
                    "返回最多 50 条匹配：file / line / text。仅扫常见文本扩展名。"
    )
    public Map<String, Object> grep(
            @AiToolParam(name = "pattern", description = "Java 正则表达式")
            String pattern,
            @AiToolParam(name = "path", description = "起始搜索目录（绝对路径）")
            String path,
            @AiToolParam(name = "maxResults", description = "结果上限（默认 50）", required = false)
            Integer maxResults
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        int cap = maxResults == null || maxResults <= 0 ? DEFAULT_MAX_RESULTS : Math.min(maxResults, 200);
        try {
            Path root = Paths.get(path).toAbsolutePath().normalize();
            if (!fileReadTool.isAllowed(root)) {
                result.put("error", "path_not_allowed");
                result.put("path", root.toString());
                return result;
            }
            if (!Files.exists(root)) {
                result.put("error", "not_found");
                result.put("path", root.toString());
                return result;
            }
            Pattern regex;
            try {
                regex = Pattern.compile(pattern);
            } catch (Exception e) {
                result.put("error", "invalid_regex");
                result.put("message", e.getMessage());
                return result;
            }
            List<Map<String, Object>> matches = new ArrayList<>();
            int[] filesScanned = {0};
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(this::isTextFile)
                        .filter(p -> {
                            try { return Files.size(p) <= MAX_FILE_BYTES; } catch (Exception e) { return false; }
                        })
                        .takeWhile(p -> matches.size() < cap)
                        .forEach(p -> {
                            filesScanned[0]++;
                            scanFile(p, regex, matches, cap);
                        });
            }
            result.put("pattern", pattern);
            result.put("rootPath", root.toString());
            result.put("filesScanned", filesScanned[0]);
            result.put("matchCount", matches.size());
            result.put("truncated", matches.size() >= cap);
            result.put("matches", matches);
            return result;
        } catch (Exception e) {
            log.warn("grep_search 失败 pattern={} path={}", pattern, path, e);
            result.put("error", "grep_failed");
            result.put("message", e.getMessage());
            return result;
        }
    }

    private boolean isTextFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        for (String ext : TEXT_EXTS) {
            if (name.endsWith(ext)) return true;
        }
        return false;
    }

    private void scanFile(Path p, Pattern regex, List<Map<String, Object>> matches, int cap) {
        try (BufferedReader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (matches.size() >= cap) return;
                if (regex.matcher(line).find()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("file", p.toString());
                    m.put("line", lineNo);
                    m.put("text", line.length() > 200 ? line.substring(0, 200) + "…" : line);
                    matches.add(m);
                }
            }
        } catch (Exception ignore) {
            // 二进制或编码错误的文件直接跳过
        }
    }
}
