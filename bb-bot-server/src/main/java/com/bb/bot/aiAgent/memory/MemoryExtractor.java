package com.bb.bot.aiAgent.memory;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.entity.AiMemorySession;
import com.bb.bot.database.aiAgent.service.IAiMemoryItemService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆抽取器：把 session 蒸馏出结构化候选卡片。
 *
 * <p>关键工程约束（见落地清单）：<b>复用 {@link MemoryCompiler#compileSessionSummary} 那一次
 * LLM 调用</b>，不为"抽记忆"再单独跑模型。本类只负责①拼接 prompt 的卡片抽取段（含已有记忆索引
 * 供去重/supersede + 写入禁令），②从合并后的 LLM 回复里解析出 {@code ```json} 卡片块，③交
 * {@link MemoryPolicy} 落库。</p>
 */
@Slf4j
@Component
public class MemoryExtractor {

    @Autowired
    private IAiMemoryItemService itemService;

    @Autowired
    private MemoryPolicy memoryPolicy;

    /** 解析合并回复里的第一个 ```json ... ``` 代码块。 */
    private static final Pattern JSON_BLOCK_RE = Pattern.compile(
            "(?s)```json\\s*(.*?)```");

    /** 拼接到 summary prompt 末尾的"抽卡片"指令段。groupId 为空时只允许 user/global scope。 */
    public String buildCardPromptSection(AiMemorySession s) {
        boolean inGroup = StringUtils.isNotBlank(s.getGroupId());
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 记忆卡片\n");
        sb.append("在上面之外，请额外判断这段对话里有没有【会影响未来回复】的长期信息，按下面规则抽成结构化卡片，");
        sb.append("用一个 ```json 代码块输出，形如 {\"cards\":[ ... ]}；没有值得记的就输出 {\"cards\":[]}。\n");
        sb.append("每张卡片字段：type, scope, summary, why, howToApply, subjectUserId, confidence(0~1), importance(0~1), expiresInDays, supersedesKey, tags。\n");
        sb.append("type 取值：user_profile(画像) / preference(行为偏好) / project_state(项目状态) / reference(外部指针) / inside_joke(群梗) / ephemeral_event(临时事件)。\n");
        if (inGroup) {
            sb.append("scope 取值：user(该用户所有对话) / group(本群公共) / user_in_group(某人在本群的关系或梗) / global(全局红线)。\n");
        } else {
            sb.append("scope 取值：user(该用户所有对话) / global(全局红线)。这是私聊，禁止输出 group / user_in_group。\n");
        }
        sb.append("硬规则：\n");
        sb.append("- preference 和 project_state 必须填 why 和 howToApply，否则不要输出该卡。\n");
        sb.append("- summary 必须是一句话、自带主语、可独立理解（它是检索命脉）。\n");
        sb.append("- 相对日期一律换算成绝对日期写进 summary（如\"周四\"→\"2026-06-05\"）。临时事件/限期项用 expiresInDays 给出相对天数。\n");
        sb.append("- 只记代码、配置、机器人人设里都【推不出来】的东西；不要记单轮流水账、临时任务态、能直接查到的事实。\n");
        sb.append("- 拿不准、置信度低的不要硬记（confidence < 0.6 的会被丢弃）。\n");

        List<AiMemoryItem> existing = loadExistingIndex(s);
        if (!existing.isEmpty()) {
            sb.append("已有记忆（如果新信息与某条冲突/更新，用 supersedesKey 指向其 key；重复的就别再输出）：\n");
            for (AiMemoryItem it : existing) {
                sb.append("- ").append(it.getMemoryKey()).append(" [").append(it.getType()).append('/')
                        .append(it.getScope()).append("] ").append(StringUtils.abbreviate(it.getSummary(), 80)).append('\n');
            }
        }
        return sb.toString();
    }

    /** 从合并回复解析候选卡片并交给 policy 落库。解析失败只记日志不抛。 */
    public void extractAndPersist(String llmAnswer, AiMemorySession s) {
        if (StringUtils.isBlank(llmAnswer) || s == null) return;
        try {
            List<MemoryCandidate> candidates = parse(llmAnswer);
            if (candidates.isEmpty()) return;
            memoryPolicy.apply(candidates, s);
        } catch (Exception e) {
            log.warn("MemoryExtractor 解析/落库失败 session={}", s.getSessionId(), e);
        }
    }

    List<MemoryCandidate> parse(String llmAnswer) {
        List<MemoryCandidate> out = new ArrayList<>();
        Matcher m = JSON_BLOCK_RE.matcher(llmAnswer);
        if (!m.find()) return out;
        String json = m.group(1).trim();
        try {
            JSONObject root = com.alibaba.fastjson2.JSON.parseObject(json);
            JSONArray cards = root == null ? null : root.getJSONArray("cards");
            if (cards == null) return out;
            for (int i = 0; i < cards.size(); i++) {
                MemoryCandidate c = cards.getObject(i, MemoryCandidate.class);
                if (c != null && StringUtils.isNotBlank(c.getSummary())) {
                    out.add(c);
                }
            }
        } catch (Exception e) {
            log.warn("MemoryExtractor JSON 卡片块解析失败：{}", StringUtils.abbreviate(json, 200));
        }
        return out;
    }

    /** 取该 session 上下文（userId / groupId）相关的 active 卡片，给 prompt 做去重/supersede 参考。 */
    private List<AiMemoryItem> loadExistingIndex(AiMemorySession s) {
        try {
            LambdaQueryWrapper<AiMemoryItem> w = new LambdaQueryWrapper<AiMemoryItem>()
                    .eq(AiMemoryItem::getStatus, MemoryStatus.ACTIVE.code())
                    .and(q -> {
                        q.eq(AiMemoryItem::getUserId, StringUtils.defaultString(s.getUserId()));
                        if (StringUtils.isNotBlank(s.getGroupId())) {
                            q.or().eq(AiMemoryItem::getGroupId, s.getGroupId());
                        }
                    })
                    .orderByDesc(AiMemoryItem::getUpdatedAt)
                    .last("limit 40");
            return itemService.list(w);
        } catch (Exception e) {
            log.debug("loadExistingIndex 失败 session={}", s.getSessionId(), e);
            return new ArrayList<>();
        }
    }
}
