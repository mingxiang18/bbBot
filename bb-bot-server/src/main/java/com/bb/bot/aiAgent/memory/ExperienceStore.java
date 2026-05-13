package com.bb.bot.aiAgent.memory;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 对应 openhanako lib/tools/experience.js 的 ExperienceStore。
 *
 * <p>布局（每用户独立子目录）：</p>
 * <pre>
 *   {workspaceDir}/user/{userId}/
 *      experience.md           ← 索引
 *      experience/
 *         {category}.md        ← 一个 category 一个文件
 * </pre>
 *
 * <p>category 名只允许 [\w\-一-鿿]，防 path traversal。</p>
 * <p>自动编号 + 去重（按 string equality）。</p>
 */
@Slf4j
@Component
public class ExperienceStore {

    @Value("${aiAgent.memory.workspaceDir:./memory-workspace}")
    private String workspaceDir;

    private static final Pattern CATEGORY_RE = Pattern.compile("^[\\w\\-\\u4e00-\\u9fff]{1,64}$");
    private static final int FILE_MAX_BYTES = 256 * 1024;

    /** 写入一条经验。返回 (file, index, entry)。 */
    public synchronized Map<String, Object> record(String userId, String category, String content) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(category) || StringUtils.isBlank(content)) {
            out.put("error", "empty_input");
            return out;
        }
        if (!CATEGORY_RE.matcher(category).matches()) {
            out.put("error", "invalid_category");
            out.put("hint", "category 仅允许字母/数字/中文/连字符，1-64 字符");
            return out;
        }
        try {
            Path file = userExperienceFile(userId, category);
            Files.createDirectories(file.getParent());
            List<String> entries = readEntries(file);
            String trimmed = content.trim();
            // 去重：与现有任意条目字符串完全相同则跳过
            for (String e : entries) {
                if (stripNumber(e).equals(trimmed)) {
                    out.put("file", file.toString());
                    out.put("status", "duplicate_skipped");
                    out.put("existing", e);
                    return out;
                }
            }
            int nextNum = entries.size() + 1;
            String newEntry = nextNum + ". " + trimmed;
            entries.add(newEntry);
            writeAll(file, entries);
            rebuildIndex(userId);
            out.put("file", file.toString());
            out.put("status", "added");
            out.put("entry", newEntry);
            return out;
        } catch (Exception e) {
            log.warn("record_experience 失败 userId={} category={}", userId, category, e);
            out.put("error", "write_failed");
            out.put("message", e.getMessage());
            return out;
        }
    }

    /** 读：不传 category 列所有 categories；传则读该 category 全部条目。 */
    public synchronized Map<String, Object> recall(String userId, String category) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (StringUtils.isBlank(userId)) {
            out.put("error", "empty_user");
            return out;
        }
        try {
            if (StringUtils.isBlank(category)) {
                Path index = userIndexFile(userId);
                if (!Files.exists(index)) {
                    out.put("categories", new ArrayList<>());
                    return out;
                }
                String content = Files.readString(index, StandardCharsets.UTF_8);
                out.put("indexFile", index.toString());
                out.put("indexContent", content);
                out.put("categories", listCategories(userId));
                return out;
            }
            if (!CATEGORY_RE.matcher(category).matches()) {
                out.put("error", "invalid_category");
                return out;
            }
            Path file = userExperienceFile(userId, category);
            if (!Files.exists(file)) {
                out.put("file", file.toString());
                out.put("entries", new ArrayList<>());
                return out;
            }
            List<String> entries = readEntries(file);
            out.put("file", file.toString());
            out.put("count", entries.size());
            out.put("entries", entries);
            return out;
        } catch (Exception e) {
            log.warn("recall_experience 失败 userId={} category={}", userId, category, e);
            out.put("error", "read_failed");
            out.put("message", e.getMessage());
            return out;
        }
    }

    /** 列该用户已有的所有 category。 */
    public List<String> listCategories(String userId) {
        Path dir = userExperienceDir(userId);
        if (!Files.exists(dir)) return new ArrayList<>();
        try (java.util.stream.Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".md"))
                    .map(p -> p.getFileName().toString().replaceAll("\\.md$", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void rebuildIndex(String userId) throws IOException {
        Path index = userIndexFile(userId);
        Files.createDirectories(index.getParent());
        List<String> cats = listCategories(userId);
        StringBuilder sb = new StringBuilder();
        sb.append("# Experience index\n\n");
        if (cats.isEmpty()) {
            sb.append("(empty)\n");
        } else {
            for (String c : cats) {
                sb.append("- ").append(c).append("\n");
            }
        }
        Files.writeString(index, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** 从文件读出所有「N. 内容」条目（去掉空行 / 标题）。 */
    private List<String> readEntries(Path file) throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        String text = Files.readString(file, StandardCharsets.UTF_8);
        List<String> result = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            result.add(t);
        }
        return result;
    }

    private void writeAll(Path file, List<String> entries) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(file.getFileName().toString().replaceAll("\\.md$", "")).append("\n\n");
        for (String e : entries) sb.append(e).append("\n");
        String text = sb.toString();
        if (text.length() > FILE_MAX_BYTES) {
            // 极端情况：从尾部截断到合规大小，保留最近条目
            int dropFromHead = text.length() - FILE_MAX_BYTES;
            text = text.substring(dropFromHead);
        }
        Files.writeString(file, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String stripNumber(String entry) {
        return entry.replaceFirst("^\\d+\\.\\s*", "").trim();
    }

    Path userRoot(String userId) {
        return Paths.get(workspaceDir, "user", safe(userId)).toAbsolutePath().normalize();
    }

    private Path userIndexFile(String userId) {
        return userRoot(userId).resolve("experience.md");
    }

    private Path userExperienceDir(String userId) {
        return userRoot(userId).resolve("experience");
    }

    private Path userExperienceFile(String userId, String category) {
        return userExperienceDir(userId).resolve(category + ".md");
    }

    private String safe(String userId) {
        return userId.replaceAll("[^\\w\\-]", "_");
    }
}
