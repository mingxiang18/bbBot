package com.bb.bot.database.splatoon.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;

/**
 * 斯普拉遁3对战记录Service接口
 * 
 * @author rym
 * @since 2024-04-02
 */
public interface ISplatoonBattleRecordService extends IService<SplatoonBattleRecord> {

    void saveBattleRecordDetail(String userAccountId, JSONObject battleRecord, JSONObject battleDetail);

    SplatoonBattleRecord saveSplatoonBattleRecord(String userAccountId, JSONObject battleRecord, JSONObject battleDetail);

    void saveSplatoonBattleUserDetail(String battleId, JSONObject battleRecord, JSONObject battleDetail);
}
