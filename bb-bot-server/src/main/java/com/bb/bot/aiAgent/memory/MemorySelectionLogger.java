package com.bb.bot.aiAgent.memory;

import com.bb.bot.database.aiAgent.entity.AiMemoryItem;
import com.bb.bot.database.aiAgent.entity.AiMemorySelectionLog;
import com.bb.bot.database.aiAgent.service.IAiMemorySelectionLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆选择审计写入（best-effort，绝不影响主回复）。单独抽成组件，便于将来开关/降级。
 */
@Slf4j
@Component
public class MemorySelectionLogger {

    @Autowired
    private IAiMemorySelectionLogService logService;

    public void log(String userId, String groupId, String queryText,
                    List<AiMemoryItem> candidates, List<AiMemoryItem> selected, String model) {
        try {
            AiMemorySelectionLog row = new AiMemorySelectionLog();
            row.setUserId(userId);
            row.setGroupId(groupId);
            row.setQueryText(StringUtils.abbreviate(StringUtils.defaultString(queryText), 500));
            row.setCandidateKeys(keys(candidates));
            row.setSelectedKeys(keys(selected));
            row.setSelectorModel(model);
            row.setCreatedAt(LocalDateTime.now());
            logService.save(row);
        } catch (Exception e) {
            log.debug("记忆选择审计写入失败（忽略）", e);
        }
    }

    private static String keys(List<AiMemoryItem> items) {
        return items == null ? "" : items.stream().map(AiMemoryItem::getMemoryKey).collect(Collectors.joining(","));
    }
}
