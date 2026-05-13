package com.bb.bot.aiAgent.skills;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 扫描 {@code aiAgent.skillsDir/<name>/SKILL.md}，按 agentskills.io 规范解析 YAML
 * frontmatter，只装载 {@code name + description}（progressive disclosure 的 metadata 层）。
 * 完整 body 由 {@link com.bb.bot.aiAgent.tools.LoadSkillTool} 按需读。
 *
 * <p>简化 YAML 解析：只支持 {@code key: value}（单行）和 {@code key: |} 块。
 * 复杂 YAML 不支持；spec 上只需要 name + description 单行字符串。</p>
 */
@Slf4j
@Component
public class SkillRegistry {

    private static final Pattern NAME_RE = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    @Value("${aiAgent.skillsDir:./skills}")
    private String skillsDir;

    private final Map<String, SkillManifest> skills = new LinkedHashMap<>();

    @EventListener
    public synchronized void onContextRefreshed(ContextRefreshedEvent ev) {
        skills.clear();
        Path root = Paths.get(skillsDir).toAbsolutePath().normalize();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            log.info("SKILLS 目录 {} 不存在，跳过", root);
            return;
        }
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory).forEach(this::tryLoad);
        } catch (IOException e) {
            log.warn("SKILLS 目录扫描失败", e);
        }
        log.info("SkillRegistry 注册完成，共 {} 个 SKILL：{}", skills.size(), skills.keySet());
    }

    private void tryLoad(Path dir) {
        Path skillMd = dir.resolve("SKILL.md");
        if (!Files.exists(skillMd)) return;
        try {
            String raw = Files.readString(skillMd, StandardCharsets.UTF_8);
            Map<String, String> fm = parseFrontmatter(raw);
            if (fm == null) {
                log.warn("SKILL 缺少 frontmatter，跳过: {}", skillMd);
                return;
            }
            String name = fm.get("name");
            String description = fm.get("description");
            if (name == null || description == null) {
                log.warn("SKILL 缺 name 或 description，跳过: {}", skillMd);
                return;
            }
            if (!NAME_RE.matcher(name).matches()) {
                log.warn("SKILL name {} 不符规范，跳过: {}", name, skillMd);
                return;
            }
            if (!name.equals(dir.getFileName().toString())) {
                log.warn("SKILL name {} 与目录名 {} 不一致，跳过", name, dir.getFileName());
                return;
            }
            skills.put(name, new SkillManifest(name, description, dir, skillMd));
        } catch (Exception e) {
            log.warn("加载 SKILL 失败: {}", skillMd, e);
        }
    }

    /**
     * 简单 YAML 解析。匹配开头的 {@code ---} 至下一个 {@code ---} 之间，
     * 每行按第一个 {@code :} 切。值前后空白会被 trim，引号会被剥掉。
     */
    private Map<String, String> parseFrontmatter(String raw) {
        String[] lines = raw.split("\\r?\\n", -1);
        if (lines.length < 2 || !lines[0].trim().equals("---")) return null;
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().equals("---")) return map;
            int idx = line.indexOf(':');
            if (idx < 0) continue;
            String key = line.substring(0, idx).trim();
            String val = line.substring(idx + 1).trim();
            // 剥引号
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                val = val.substring(1, val.length() - 1);
            }
            map.put(key, val);
        }
        return null;  // 没找到收尾 ---
    }

    public Collection<SkillManifest> all() {
        return skills.values();
    }

    public SkillManifest get(String name) {
        return skills.get(name);
    }

    /**
     * 拼出给 LLM 看的 "skill 目录" 文本，注入 system prompt。
     * progressive disclosure：先只把 name + 简短描述给它，它觉得相关时再调 load_skill。
     */
    public String describeAllForSystemPrompt() {
        if (skills.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n你可调用以下 SKILLS（用 load_skill 工具按 name 取详细指引后再执行）:\n");
        for (SkillManifest s : skills.values()) {
            sb.append("- ").append(s.getName()).append(": ").append(s.getDescription()).append("\n");
        }
        return sb.toString();
    }

    public List<String> names() {
        return new ArrayList<>(skills.keySet());
    }
}
