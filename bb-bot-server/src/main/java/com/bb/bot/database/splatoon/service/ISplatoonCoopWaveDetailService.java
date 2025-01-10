package com.bb.bot.database.splatoon.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bb.bot.database.splatoon.entity.SplatoonCoopWaveDetail;

/**
 * 斯普拉遁3打工波数详情Service接口
 * 
 * @author rym
 * @since 2024-02-01
 */
public interface ISplatoonCoopWaveDetailService extends IService<SplatoonCoopWaveDetail> {

    /**
     * 从splatoon接口数据保存打工wave数详情
     */
    void saveSplatoonCoopWaveDetail(String coopId, JSONObject coopRecord, JSONObject coopDetail);
}
