package com.bb.bot.database.splatoon.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.DateUtils;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.mapper.SplatoonCoopRecordsMapper;
import com.bb.bot.database.splatoon.service.ISplatoonCoopEnemyDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopUserDetailService;
import com.bb.bot.database.splatoon.service.ISplatoonCoopWaveDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.bb.bot.database.splatoon.service.ISplatoonCoopRecordsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 斯普拉遁3打工记录Service业务层处理
 *
 * @author rym
 * @since 2024-02-01
 */
@Service
public class SplatoonCoopRecordsServiceImpl extends ServiceImpl<SplatoonCoopRecordsMapper, SplatoonCoopRecord> implements ISplatoonCoopRecordsService {
    @Autowired
    private SplatoonCoopRecordsMapper splatoonCoopRecordsMapper;

    @Autowired
    private ISplatoonCoopUserDetailService coopUserDetailService;

    @Autowired
    private ISplatoonCoopWaveDetailService coopWaveDetailService;

    @Autowired
    private ISplatoonCoopEnemyDetailService coopEnemyDetailService;

    @Autowired
    private ResourcesUtils resourcesUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCoopRecordDetail(String userId, JSONObject coopRecord, JSONObject coopDetail) {
        //保存打工数据
        SplatoonCoopRecord splatoonCoopRecord = saveSplatoonCoopRecord(userId, coopRecord, coopDetail);

        //保存打工玩家数据详情
        coopUserDetailService.saveSplatoonCoopUserDetail(String.valueOf(splatoonCoopRecord.getId()), coopRecord, coopDetail);

        //保存打工wave数据详情
        coopWaveDetailService.saveSplatoonCoopWaveDetail(String.valueOf(splatoonCoopRecord.getId()), coopRecord, coopDetail);

        //保存打工敌人击倒数据详情
        coopEnemyDetailService.saveSplatoonCoopEnemyDetail(String.valueOf(splatoonCoopRecord.getId()), coopRecord, coopDetail);
    }


    @Override
    public SplatoonCoopRecord saveSplatoonCoopRecord(String userId, JSONObject coopRecord, JSONObject coopDetail) {
        SplatoonCoopRecord splatoonCoopRecord = new SplatoonCoopRecord();
        splatoonCoopRecord.setAppCoopId(coopRecord.getString("id"));
        splatoonCoopRecord.setUserId(userId);
        splatoonCoopRecord.setRule(coopDetail.getString("rule"));
        splatoonCoopRecord.setPlayedTime(DateUtils.convertUTCTimeToCNLocalDateTime(coopDetail.getString("playedTime")));

        //设置地图数据
        splatoonCoopRecord.setCoopStageId(coopDetail.getJSONObject("coopStage").getString("id"));
        splatoonCoopRecord.setCoopStageName(coopDetail.getJSONObject("coopStage").getString("name"));
        resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/coop/stage/" + splatoonCoopRecord.getCoopStageName() + ".png",
                coopDetail.getJSONObject("coopStage").getJSONObject("image").getString("url"));

        splatoonCoopRecord.setDangerRate(String.valueOf(coopDetail.getBigDecimal("dangerRate").multiply(new BigDecimal(100)).intValue()));
        if(coopDetail.getJSONObject("afterGrade") != null) {
            splatoonCoopRecord.setAfterGradeId(coopDetail.getJSONObject("afterGrade").getString("id"));
            splatoonCoopRecord.setAfterGradeName(coopDetail.getJSONObject("afterGrade").getString("name"));
        }
        splatoonCoopRecord.setAfterGradePoint(coopDetail.getInteger("afterGradePoint"));
        splatoonCoopRecord.setGradePointDiff(coopRecord.getString("gradePointDiff"));
        splatoonCoopRecord.setResultWave(coopDetail.getInteger("resultWave"));

        //计算运蛋数
        if (coopRecord.getJSONObject("myResult") != null) {
            //计算红蛋数
            int countRed = coopRecord.getJSONObject("myResult").getInteger("deliverCount");
            for (Object memberResult : coopRecord.getJSONArray("memberResults")) {
                Integer deliverCount = ((JSONObject) memberResult).getInteger("deliverCount");
                if (deliverCount != null) {
                    countRed += deliverCount;
                }
            }
            splatoonCoopRecord.setTeamRedCount(countRed);
        }
        //计算金蛋运蛋数
        if (coopRecord.getJSONArray("waveResults").size() > 0) {
            int countGloden = 0;
            for (Object waveResult : coopRecord.getJSONArray("waveResults")) {
                Integer teamDeliverCount = ((JSONObject) waveResult).getInteger("teamDeliverCount");
                if (teamDeliverCount != null) {
                    countGloden += teamDeliverCount;
                }
            }
            splatoonCoopRecord.setTeamGlodenCount(countGloden);
        }

        //判断是否打了boss
        if (coopRecord.getJSONObject("bossResult") != null) {
            JSONObject boss = coopRecord.getJSONObject("bossResult").getJSONObject("boss");
            splatoonCoopRecord.setBossId(boss.getString("id"));
            splatoonCoopRecord.setBossName(boss.getString("name"));
            splatoonCoopRecord.setBossDefeatFlag(coopRecord.getJSONObject("bossResult").getBoolean("hasDefeatBoss"));
            //下载头目鲑鱼立绘(按 bossId),供详情/列表展示
            if (boss.getJSONObject("image") != null && boss.getJSONObject("image").getString("url") != null) {
                resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/coop/boss/" + boss.getString("id") + ".png",
                        boss.getJSONObject("image").getString("url"));
            }
            //设置鳞片数量
            splatoonCoopRecord.setGoldScale(coopDetail.getJSONObject("scale").getInteger("gold"));
            splatoonCoopRecord.setSilverScale(coopDetail.getJSONObject("scale").getInteger("silver"));
            splatoonCoopRecord.setBronzeScale(coopDetail.getJSONObject("scale").getInteger("bronze"));
        }

        //设置武器名
        JSONArray weaponsArray = coopRecord.getJSONArray("weapons");
        if (weaponsArray.size() > 0) {
            splatoonCoopRecord.setWeapon1(weaponsArray.getJSONObject(0).getString("name"));
            resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/coop/weapon/" + splatoonCoopRecord.getWeapon1() + ".png",
                    weaponsArray.getJSONObject(0).getJSONObject("image").getString("url"));
        }
        if (weaponsArray.size() > 1) {
            splatoonCoopRecord.setWeapon2(weaponsArray.getJSONObject(1).getString("name"));
            resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/coop/weapon/" + splatoonCoopRecord.getWeapon2() + ".png",
                    weaponsArray.getJSONObject(1).getJSONObject("image").getString("url"));
        }
        if (weaponsArray.size() > 2) {
            splatoonCoopRecord.setWeapon3(weaponsArray.getJSONObject(2).getString("name"));
            resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/coop/weapon/" + splatoonCoopRecord.getWeapon3() + ".png",
                    weaponsArray.getJSONObject(2).getJSONObject("image").getString("url"));
        }
        if (weaponsArray.size() > 3) {
            splatoonCoopRecord.setWeapon4(weaponsArray.getJSONObject(3).getString("name"));
            resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/coop/weapon/" + splatoonCoopRecord.getWeapon4() + ".png",
                    weaponsArray.getJSONObject(3).getJSONObject("image").getString("url"));
        }

        //得分 / 熊先生点数 / 气味计
        splatoonCoopRecord.setJobScore(coopDetail.getInteger("jobScore"));
        splatoonCoopRecord.setJobBonus(coopDetail.getInteger("jobBonus"));
        splatoonCoopRecord.setSmellMeter(coopDetail.getInteger("smellMeter"));

        //Wave 概要: "W1 28·W2 24·W3 31"(送金蛋数=teamDeliverCount)
        JSONArray waveResults = coopRecord.getJSONArray("waveResults");
        if (waveResults != null && !waveResults.isEmpty()) {
            StringBuilder wave = new StringBuilder();
            for (int i = 0; i < waveResults.size(); i++) {
                JSONObject w = waveResults.getJSONObject(i);
                Integer wn = w.getInteger("waveNumber");
                Integer deliver = w.getInteger("teamDeliverCount");
                if (wn == null) {
                    continue;
                }
                if (wave.length() > 0) {
                    wave.append("·");
                }
                wave.append("W").append(wn).append(" ").append(deliver == null ? "-" : deliver);
            }
            if (wave.length() > 0) {
                splatoonCoopRecord.setWaveInfo(wave.toString());
            }
        }

        splatoonCoopRecordsMapper.insert(splatoonCoopRecord);
        return splatoonCoopRecord;
    }
}
