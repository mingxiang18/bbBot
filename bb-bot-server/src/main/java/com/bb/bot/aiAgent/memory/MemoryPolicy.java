package com.bb.bot.aiAgent.memory;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.entity.AiMemorySession;
import com.bb.bot.database.aiAgent.service.IAiMemoryItemService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 记忆写入策略器：决定候选卡片如何落库。
 *
 * <p>刻意做轻（学 Claude Code：CC 不做语义冲突检测，靠"注入端老化警告 + 用时验证"防过时误导）。
 * 主路径只有 insert / refresh(已有同义卡更 last_seen) / supersede(LLM 明确指了 supersedesKey) / ignore。
 * 不在写入期做复杂的 LLM 语义比对——那是易错又费模型的活。</p>
 */
@Slf4j
@Component
public class MemoryPolicy {

    @Autowired
    private IAiMemoryItemService itemService;

    /** 低于该置信度的候选直接丢弃，不进库（落地清单决策：低置信度不进 active）。 */
    @Value("${aiAgent.memory.minConfidence:0.6}")
    private double minConfidence;

    /** project_state 默认过期天数（决策 3）。 */
    @Value("${aiAgent.memory.projectStateTtlDays:14}")
    private int projectStateTtlDays;

    public synchronized void apply(List<MemoryCandidate> candidates, AiMemorySession s) {
        if (candidates == null || candidates.isEmpty() || s == null) return;
        int insert = 0, refresh = 0, supersede = 0, ignore = 0;
        for (MemoryCandidate c : candidates) {
            try {
                Decision d = applyOne(c, s);
                switch (d) {
                    case INSERT -> insert++;
                    case REFRESH -> refresh++;
                    case SUPERSEDE -> supersede++;
                    default -> ignore++;
                }
            } catch (Exception e) {
                ignore++;
                log.warn("MemoryPolicy 处理候选失败 session={} summary={}", s.getSessionId(),
                        StringUtils.abbreviate(c.getSummary(), 60), e);
            }
        }
        log.info("MemoryPolicy session={} 候选={} insert={} refresh={} supersede={} ignore={}",
                s.getSessionId(), candidates.size(), insert, refresh, supersede, ignore);
    }

    private enum Decision { INSERT, REFRESH, SUPERSEDE, IGNORE }

    private Decision applyOne(MemoryCandidate c, AiMemorySession s) {
        MemoryType type = MemoryType.parse(c.getType());
        MemoryScope scope = MemoryScope.parse(c.getScope());
        if (type == null || scope == null || StringUtils.isBlank(c.getSummary())) {
            return Decision.IGNORE;
        }
        // 置信度门槛
        double conf = c.getConfidence() == null ? 0.0 : c.getConfidence();
        if (conf < minConfidence) {
            return Decision.IGNORE;
        }
        // preference / project_state 必须有 why + howToApply
        if ((type == MemoryType.PREFERENCE || type == MemoryType.PROJECT_STATE)
                && (StringUtils.isBlank(c.getWhy()) || StringUtils.isBlank(c.getHowToApply()))) {
            return Decision.IGNORE;
        }
        // scope 合法性：私聊（无 groupId）不得有 group / user_in_group，降级为 user
        boolean inGroup = StringUtils.isNotBlank(s.getGroupId());
        if (scope.needsGroup() && !inGroup) {
            scope = MemoryScope.USER;
        }

        // 解析 scope → owner 字段
        String userId = scope.needsUser() ? defaultUser(s) : null;
        String groupId = scope.needsGroup() ? s.getGroupId() : null;
        String subjectUserId = StringUtils.isNotBlank(c.getSubjectUserId())
                ? c.getSubjectUserId()
                : (scope == MemoryScope.USER_IN_GROUP ? defaultUser(s) : null);

        // 预生成新卡 key，便于 supersede 时回填旧卡的 superseded_by 形成审计链
        String newKey = generateKey();

        // supersede：LLM 明确指了要替代的旧卡 key
        boolean didSupersede = false;
        if (StringUtils.isNotBlank(c.getSupersedesKey())) {
            didSupersede = supersedeOld(c.getSupersedesKey(), newKey);
        }

        // 去重/refresh：同 owner + 同 type + 同 scope + summary 归一化相等的 active 卡，只更新 last_seen
        AiMemoryItem dup = findDuplicate(type, scope, userId, groupId, c.getSummary());
        if (dup != null && !didSupersede) {
            dup.setLastSeenAt(LocalDateTime.now());
            if (c.getConfidence() != null) {
                dup.setConfidence(BigDecimal.valueOf(conf));
            }
            itemService.updateById(dup);
            return Decision.REFRESH;
        }

        // 新增
        AiMemoryItem row = new AiMemoryItem();
        row.setMemoryKey(newKey);
        row.setType(type.code());
        row.setScope(scope.code());
        row.setUserId(userId);
        row.setGroupId(groupId);
        row.setSubjectUserId(subjectUserId);
        row.setSummary(StringUtils.abbreviate(c.getSummary(), 1000));
        row.setBody(c.getBody());
        row.setWhy(c.getWhy());
        row.setHowToApply(c.getHowToApply());
        row.setEvidence(c.getEvidence() == null ? null : JSON.toJSONString(c.getEvidence()));
        row.setTags(JSON.toJSONString(c.getTags() == null ? Collections.emptyList() : c.getTags()));
        row.setSearchText(FactStore.normalize(
                StringUtils.joinWith(" ", c.getSummary(),
                        StringUtils.defaultString(c.getWhy()), StringUtils.defaultString(c.getHowToApply()))));
        row.setStatus(MemoryStatus.ACTIVE.code());
        row.setConfidence(BigDecimal.valueOf(conf));
        row.setImportance(c.getImportance() == null ? null : BigDecimal.valueOf(c.getImportance()));
        row.setExpiresAt(resolveExpiry(type, c.getExpiresInDays()));
        row.setLastSeenAt(LocalDateTime.now());
        row.setSourceSessionId(s.getSessionId());
        itemService.save(row);
        return didSupersede ? Decision.SUPERSEDE : Decision.INSERT;
    }

    /** 把旧卡标 superseded 并回填 superseded_by=新卡 key（保留审计链，不硬删）；返回是否命中。 */
    private boolean supersedeOld(String oldKey, String newKey) {
        AiMemoryItem old = itemService.getOne(new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getMemoryKey, oldKey)
                .last("limit 1"));
        if (old == null || MemoryStatus.SUPERSEDED.code().equals(old.getStatus())
                || MemoryStatus.DELETED.code().equals(old.getStatus())) {
            return false;
        }
        old.setStatus(MemoryStatus.SUPERSEDED.code());
        old.setSupersededBy(newKey);
        itemService.updateById(old);
        return true;
    }

    private AiMemoryItem findDuplicate(MemoryType type, MemoryScope scope,
                                       String userId, String groupId, String summary) {
        String norm = FactStore.normalize(summary);
        if (StringUtils.isBlank(norm)) return null;
        List<AiMemoryItem> sameBucket = itemService.list(new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getStatus, MemoryStatus.ACTIVE.code())
                .eq(AiMemoryItem::getType, type.code())
                .eq(AiMemoryItem::getScope, scope.code())
                .eq(userId != null, AiMemoryItem::getUserId, userId)
                .eq(groupId != null, AiMemoryItem::getGroupId, groupId)
                .orderByDesc(AiMemoryItem::getUpdatedAt)
                .last("limit 50"));
        for (AiMemoryItem it : sameBucket) {
            if (norm.equals(FactStore.normalize(it.getSummary()))) {
                return it;
            }
        }
        return null;
    }

    private LocalDateTime resolveExpiry(MemoryType type, Integer expiresInDays) {
        if (expiresInDays != null && expiresInDays > 0) {
            return LocalDateTime.now().plusDays(expiresInDays);
        }
        if (type == MemoryType.PROJECT_STATE) {
            return LocalDateTime.now().plusDays(projectStateTtlDays);
        }
        return null;
    }

    private static String defaultUser(AiMemorySession s) {
        return StringUtils.isBlank(s.getUserId()) ? "_anonymous" : s.getUserId();
    }

    private static String generateKey() {
        return "m_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
