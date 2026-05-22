package com.bb.bot.database.aiAgent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bb.bot.database.aiAgent.entity.AiTokenUsage;
import com.bb.bot.database.aiAgent.mapper.AiTokenUsageMapper;
import com.bb.bot.database.aiAgent.service.IAiTokenUsageService;
import com.bb.bot.database.aiAgent.vo.UserModelUsage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiTokenUsageServiceImpl extends ServiceImpl<AiTokenUsageMapper, AiTokenUsage>
        implements IAiTokenUsageService {

    @Override
    public long sumTotalTokensByUser(String userId) {
        QueryWrapper<AiTokenUsage> qw = new QueryWrapper<>();
        qw.select("IFNULL(SUM(total_tokens),0) AS s").eq("user_id", userId);
        List<Map<String, Object>> rows = baseMapper.selectMaps(qw);
        if (rows.isEmpty() || rows.get(0).get("s") == null) {
            return 0L;
        }
        return ((Number) rows.get(0).get("s")).longValue();
    }

    @Override
    public List<UserModelUsage> aggregateByUserAndModel(String userId) {
        QueryWrapper<AiTokenUsage> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        return aggregate(qw);
    }

    @Override
    public List<UserModelUsage> aggregate(LocalDate from, LocalDate to) {
        QueryWrapper<AiTokenUsage> qw = new QueryWrapper<>();
        if (from != null) {
            qw.ge("created_at", from.atStartOfDay());
        }
        if (to != null) {
            qw.le("created_at", to.atTime(LocalTime.MAX));
        }
        return aggregate(qw);
    }

    private List<UserModelUsage> aggregate(QueryWrapper<AiTokenUsage> qw) {
        qw.select("user_id AS user_id",
                "model AS model",
                "IFNULL(SUM(prompt_tokens),0) AS sum_prompt",
                "IFNULL(SUM(completion_tokens),0) AS sum_completion",
                "IFNULL(SUM(total_tokens),0) AS sum_total",
                "COUNT(*) AS call_count");
        qw.groupBy("user_id", "model");
        List<Map<String, Object>> rows = baseMapper.selectMaps(qw);
        List<UserModelUsage> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            UserModelUsage u = new UserModelUsage();
            u.setUserId(StringUtils.defaultString(asString(r.get("user_id"))));
            u.setModel(asString(r.get("model")));
            u.setSumPromptTokens(asLong(r.get("sum_prompt")));
            u.setSumCompletionTokens(asLong(r.get("sum_completion")));
            u.setSumTotalTokens(asLong(r.get("sum_total")));
            u.setCallCount(asLong(r.get("call_count")));
            out.add(u);
        }
        return out;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long asLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
