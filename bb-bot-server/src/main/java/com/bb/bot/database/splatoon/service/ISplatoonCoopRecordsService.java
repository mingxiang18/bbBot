package com.bb.bot.database.splatoon.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import org.springframework.transaction.annotation.Transactional;

/**
 * 斯普拉遁3打工记录Service接口
 * 
 * @author rym
 * @date 2024-02-01
 */
public interface ISplatoonCoopRecordsService extends IService<SplatoonCoopRecord> {

    @Transactional(rollbackFor = Exception.class)
    void saveCoopRecordDetail(String userId, JSONObject coopRecord, JSONObject coopDetail);

    /**
     * 从splatoon接口数据保存打工记录
     */
    SplatoonCoopRecord saveSplatoonCoopRecord(String userId, JSONObject coopRecord, JSONObject coopDetail);
}
