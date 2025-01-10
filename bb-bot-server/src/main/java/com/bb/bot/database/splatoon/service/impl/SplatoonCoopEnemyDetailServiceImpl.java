package com.bb.bot.database.splatoon.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.database.splatoon.entity.SplatoonCoopEnemyDetail;
import com.bb.bot.database.splatoon.entity.SplatoonCoopWaveDetail;
import com.bb.bot.database.splatoon.mapper.SplatoonCoopEnemyDetailMapper;
import com.bb.bot.database.splatoon.service.ISplatoonCoopEnemyDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 斯普拉遁3打工boss详情Service业务层处理
 *
 * @author rym
 * @since 2024-02-01
 */
@Service
public class SplatoonCoopEnemyDetailServiceImpl extends ServiceImpl<SplatoonCoopEnemyDetailMapper, SplatoonCoopEnemyDetail> implements ISplatoonCoopEnemyDetailService {
    @Autowired
    private SplatoonCoopEnemyDetailMapper splatoonCoopEnemyDetailMapper;


    @Override
    public void saveSplatoonCoopEnemyDetail(String coopId, JSONObject coopRecord, JSONObject coopDetail) {
        List<SplatoonCoopEnemyDetail> enemyDetailList = new ArrayList<>();

        JSONArray enemyResultArray = coopDetail.getJSONArray("enemyResults");
        //遍历波数记录
        for (Object enemyResult : enemyResultArray) {
            JSONObject enemyResultObject = (JSONObject) enemyResult;

            //封装实体类
            SplatoonCoopEnemyDetail splatoonCoopEnemyDetail = new SplatoonCoopEnemyDetail();
            splatoonCoopEnemyDetail.setCoopId(coopId);
            splatoonCoopEnemyDetail.setEnemyId(enemyResultObject.getJSONObject("enemy").getString("id"));
            splatoonCoopEnemyDetail.setEnemyName(enemyResultObject.getJSONObject("enemy").getString("name"));
            splatoonCoopEnemyDetail.setDefeatCount(enemyResultObject.getInteger("defeatCount"));
            splatoonCoopEnemyDetail.setTeamDefeatCount(enemyResultObject.getInteger("teamDefeatCount"));
            splatoonCoopEnemyDetail.setPopCount(enemyResultObject.getInteger("popCount"));

            //添加到列表
            enemyDetailList.add(splatoonCoopEnemyDetail);
        }

        //批量保存
        saveBatch(enemyDetailList);
    }
}
