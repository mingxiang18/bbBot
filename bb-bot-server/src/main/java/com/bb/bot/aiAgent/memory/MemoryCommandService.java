package com.bb.bot.aiAgent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.service.IAiMemoryItemService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户/owner 可控记忆操作（Phase 4）：显式写入、遗忘、查看、单卡读取、软删、手动 supersede。
 *
 * <p>被 {@code BbAiMemoryHandler}（owner 命令）和 {@code MemoryNaturalLanguageRouter}
 * （自然语言意图）共用。普通用户只能操作【本人】(user_id=caller) 的卡片，强 userId 校验。</p>
 */
@Slf4j
@Component
public class MemoryCommandService {

    @Autowired
    private IAiMemoryItemService itemService;

    /** 显式"记住 X"：写一张本人 user-scope 卡片，立即生效。返回确认文案。 */
    public String writeExplicit(String userId, String factText) {
        String fact = StringUtils.trimToEmpty(factText);
        if (StringUtils.isBlank(userId) || fact.isEmpty()) {
            return "没听清要记什么呢，可以说「记住：……」";
        }
        if (fact.length() > 500) fact = fact.substring(0, 500);
        // 含偏好语气 → preference（补默认 why/howToApply 以满足约束）；否则 user_profile
        boolean pref = fact.matches(".*(喜欢|不喜欢|别|不要|讨厌|希望你|以后).*");
        AiMemoryItem row = new AiMemoryItem();
        row.setMemoryKey(genKey());
        row.setType((pref ? MemoryType.PREFERENCE : MemoryType.USER_PROFILE).code());
        row.setScope(MemoryScope.USER.code());
        row.setUserId(userId);
        row.setSummary(fact);
        if (pref) {
            row.setWhy("用户明确要求记住");
            row.setHowToApply("在后续回复中遵循这条偏好");
        }
        row.setSearchText(FactStore.normalize(fact));
        row.setStatus(MemoryStatus.ACTIVE.code());
        row.setConfidence(BigDecimal.valueOf(1.0));
        row.setImportance(BigDecimal.valueOf(0.8));
        row.setLastSeenAt(LocalDateTime.now());
        itemService.save(row);
        return "好的，记住了：" + fact + "（" + row.getMemoryKey() + "）";
    }

    /** "忘掉 X"：把本人匹配 X 的 active/stale 卡片标 deleted。返回删除条数。 */
    public int forget(String userId, String query) {
        String q = StringUtils.trimToEmpty(query);
        if (StringUtils.isBlank(userId) || q.isEmpty()) return 0;
        String norm = FactStore.normalize(q);
        List<AiMemoryItem> mine = itemService.list(new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getUserId, userId)
                .in(AiMemoryItem::getStatus, List.of(MemoryStatus.ACTIVE.code(), MemoryStatus.STALE.code()))
                .last("limit 200"));
        int n = 0;
        for (AiMemoryItem it : mine) {
            String s = FactStore.normalize(it.getSummary());
            if (s.contains(norm) || norm.contains(s)) {
                it.setStatus(MemoryStatus.DELETED.code());
                itemService.updateById(it);
                n++;
            }
        }
        return n;
    }

    /** "你记得我什么"：展示本人(user_id=caller) 的可读卡片清单。普通用户只看自己的。 */
    public String readableSelfMemory(String userId) {
        if (StringUtils.isBlank(userId)) return "（还没有关于你的记忆）";
        List<AiMemoryItem> mine = itemService.list(new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getUserId, userId)
                .in(AiMemoryItem::getStatus, List.of(MemoryStatus.ACTIVE.code(), MemoryStatus.STALE.code()))
                .orderByDesc(AiMemoryItem::getImportance)
                .orderByDesc(AiMemoryItem::getUpdatedAt)
                .last("limit 30"));
        if (mine.isEmpty()) return "我还没记住关于你的长期信息呢～";
        StringBuilder sb = new StringBuilder("关于你，我记得这些：\n");
        for (AiMemoryItem it : mine) {
            sb.append("• ").append(it.getSummary());
            if (MemoryStatus.STALE.code().equals(it.getStatus())) sb.append("（可能过期）");
            sb.append("  [").append(it.getMemoryKey()).append("]\n");
        }
        sb.append("如果有记错的，可以说「忘掉……」让我删掉。");
        return sb.toString().trim();
    }

    public AiMemoryItem getByKey(String memoryKey) {
        if (StringUtils.isBlank(memoryKey)) return null;
        return itemService.getOne(new LambdaQueryWrapper<AiMemoryItem>()
                .eq(AiMemoryItem::getMemoryKey, memoryKey).last("limit 1"));
    }

    /** owner 列出某用户的卡片索引。 */
    public List<AiMemoryItem> listForOwner(String userId, int limit) {
        LambdaQueryWrapper<AiMemoryItem> w = new LambdaQueryWrapper<AiMemoryItem>()
                .ne(AiMemoryItem::getStatus, MemoryStatus.DELETED.code())
                .orderByDesc(AiMemoryItem::getUpdatedAt)
                .last("limit " + Math.min(Math.max(limit, 1), 100));
        if (StringUtils.isNotBlank(userId)) w.eq(AiMemoryItem::getUserId, userId);
        return itemService.list(w);
    }

    /** 软删一张卡片（按 key）。 */
    public boolean softDelete(String memoryKey) {
        AiMemoryItem it = getByKey(memoryKey);
        if (it == null) return false;
        it.setStatus(MemoryStatus.DELETED.code());
        itemService.updateById(it);
        return true;
    }

    /** 手动 supersede：旧卡降级 superseded + 记 superseded_by。 */
    public boolean supersede(String oldKey, String newKey) {
        AiMemoryItem oldIt = getByKey(oldKey);
        if (oldIt == null) return false;
        oldIt.setStatus(MemoryStatus.SUPERSEDED.code());
        if (StringUtils.isNotBlank(newKey)) oldIt.setSupersededBy(newKey);
        itemService.updateById(oldIt);
        return true;
    }

    public static String genKey() {
        return "m_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
