package com.bb.bot.aiAgent.memory;

/**
 * 记忆卡片生命周期状态。
 *
 * <ul>
 *   <li>{@link #ACTIVE} 生效中，参与检索注入</li>
 *   <li>{@link #STALE} 已老化（过期/长期未确认），仍可检索但注入时带"可能过期"警告、降权</li>
 *   <li>{@link #SUPERSEDED} 被新卡片替代，记 superseded_by，保留审计、不再注入</li>
 *   <li>{@link #DELETED} 用户/owner 删除，软删不再使用</li>
 * </ul>
 */
public enum MemoryStatus {
    ACTIVE,
    STALE,
    SUPERSEDED,
    DELETED;

    public String code() {
        return name().toLowerCase();
    }
}
