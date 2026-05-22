package com.bb.bot.database.aiAgent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bb.bot.database.aiAgent.entity.AiTokenUsage;
import com.bb.bot.database.aiAgent.vo.UserModelUsage;

import java.time.LocalDate;
import java.util.List;

/**
 * Token 用量查询接口，供其他 service 调用。
 */
public interface IAiTokenUsageService extends IService<AiTokenUsage> {

    /** 某用户累计消耗的总 token 数。 */
    long sumTotalTokensByUser(String userId);

    /** 某用户按模型聚合的用量明细。 */
    List<UserModelUsage> aggregateByUserAndModel(String userId);

    /** 指定时间区间内，全部用户按 用户+模型 聚合的用量（含两端，from/to 可为 null 表示不限）。 */
    List<UserModelUsage> aggregate(LocalDate from, LocalDate to);
}
