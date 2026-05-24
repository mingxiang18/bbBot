package com.bb.bot.database.aiAgent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bb.bot.database.aiAgent.entity.AiMemoryFact;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AiMemoryFactMapper extends BaseMapper<AiMemoryFact> {

    /**
     * 用 MySQL FULLTEXT MATCH 检索（要求列上有 ngram FULLTEXT 索引）。
     * 兼容失败时 service 层 fallback 到 LIKE 搜索。
     */
    @Select("SELECT * FROM ai_memory_fact " +
            "WHERE user_id = #{userId} " +
            "AND MATCH(search_text) AGAINST (#{query} IN NATURAL LANGUAGE MODE) " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<AiMemoryFact> fulltextSearch(@Param("userId") String userId,
                                       @Param("query") String query,
                                       @Param("limit") int limit);
}
