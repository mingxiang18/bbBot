package com.bb.bot.aiAgent.skills;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiSkill;
import com.bb.bot.database.aiAgent.service.IAiSkillService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SKILL 注册表，从 {@code ai_skill} 表加载（progressive disclosure 的 metadata 层，
 * 只装载 name + description，完整 body 由
 * {@link com.bb.bot.aiAgent.tools.LoadSkillTool} 按需取）。
 *
 * <p>SKILL 的增删改直接操作 {@code ai_skill} 表，{@code /aiAgent.skill.reload}
 * 可在不重启的情况下重新加载。</p>
 */
@Slf4j
@Component
public class SkillRegistry {

    private static final Pattern NAME_RE = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    @Autowired
    private IAiSkillService aiSkillService;

    private final Map<String, SkillManifest> skills = new LinkedHashMap<>();

    @EventListener
    public synchronized void onContextRefreshed(ContextRefreshedEvent ev) {
        reload();
    }

    /**
     * 重新加载全部 SKILL（{@code ai_skill} 表）。可被管理命令调用，无需重启。
     *
     * @return 加载完成后的 skill 名列表
     */
    public synchronized List<String> reload() {
        skills.clear();
        int count = loadFromDatabase();
        log.info("SkillRegistry 注册完成，共 {} 个 SKILL：{}", count, skills.keySet());
        return names();
    }

    /** 加载 ai_skill 表中启用的行，返回成功加载的数量。 */
    private int loadFromDatabase() {
        List<AiSkill> rows;
        try {
            rows = aiSkillService.list(new LambdaQueryWrapper<AiSkill>()
                    .eq(AiSkill::getEnabled, true));
        } catch (Exception e) {
            // 表尚未建好 / DB 不可用：本轮无 skill，待 reload 重试
            log.warn("ai_skill 表加载失败，本轮无 SKILL：{}", e.getMessage());
            return 0;
        }
        int count = 0;
        for (AiSkill row : rows) {
            String name = row.getName();
            if (StringUtils.isAnyBlank(name, row.getDescription(), row.getBody())) {
                log.warn("ai_skill 行缺 name/description/body，跳过: id={}", row.getId());
                continue;
            }
            if (!NAME_RE.matcher(name).matches()) {
                log.warn("ai_skill name {} 不符规范（小写+连字符），跳过", name);
                continue;
            }
            if (skills.containsKey(name)) {
                log.warn("ai_skill 存在重名 SKILL {}，后者覆盖前者", name);
            }
            skills.put(name, new SkillManifest(name, row.getDescription(), row.getBody()));
            count++;
        }
        return count;
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
