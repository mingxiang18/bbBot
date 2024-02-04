package com.bb.bot.database.splatoon.service;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bb.bot.database.splatoon.entity.SplatoonCoopUserDetail;

/**
 * 斯普拉遁3玩家打工详情Service接口
 * 
 * @author rym
 * @date 2024-02-01
 */
public interface ISplatoonCoopUserDetailService extends IService<SplatoonCoopUserDetail> {

    /**
     * 从splatoon接口数据保存打工用户详情
     */
    void saveSplatoonCoopUserDetail(String coopId, JSONObject coopRecord, JSONObject coopDetail);
}
