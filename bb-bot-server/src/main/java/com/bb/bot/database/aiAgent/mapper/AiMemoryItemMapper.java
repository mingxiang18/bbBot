package com.bb.bot.database.aiAgent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryItem;

/**
 * 结构化记忆卡片 mapper。Phase 2 只用 BaseMapper CRUD；
 * Phase 3 的 selector 粗筛（ngram FULLTEXT + scope 过滤）再在此扩自定义查询。
 */
public interface AiMemoryItemMapper extends BaseMapper<AiMemoryItem> {
}
