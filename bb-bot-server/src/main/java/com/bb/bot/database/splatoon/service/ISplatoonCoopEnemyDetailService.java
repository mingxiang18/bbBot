package com.bb.bot.database.splatoon.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bb.bot.database.splatoon.entity.SplatoonCoopEnemyDetail;

/**
 * 斯普拉遁3打工敌人详情Service接口
 * 
 * @author rym
 * @date 2024-02-01
 */
public interface ISplatoonCoopEnemyDetailService extends IService<SplatoonCoopEnemyDetail> {

    /**
     * 从splatoon接口数据保存敌人击倒数详情
     */
    void saveSplatoonCoopEnemyDetail(String coopId, JSONObject coopRecord, JSONObject coopDetail);
}
