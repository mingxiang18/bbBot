package com.bb.bot.database.splatoon.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.database.splatoon.entity.SplatoonCoopWaveDetail;
import com.bb.bot.database.splatoon.mapper.SplatoonCoopWaveDetailMapper;
import com.bb.bot.database.splatoon.service.ISplatoonCoopWaveDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 斯普拉遁3打工波数详情Service业务层处理
 *
 * @author rym
 * @date 2024-02-01
 */
@Service
public class SplatoonCoopWaveDetailServiceImpl extends ServiceImpl<SplatoonCoopWaveDetailMapper, SplatoonCoopWaveDetail> implements ISplatoonCoopWaveDetailService {
    @Autowired
    private SplatoonCoopWaveDetailMapper splatoonCoopWaveDetailMapper;

    @Override
    public void saveSplatoonCoopWaveDetail(String coopId, JSONObject coopRecord, JSONObject coopDetail) {
        List<SplatoonCoopWaveDetail> waveDetailList = new ArrayList<>();

        JSONArray waveResultArray = coopDetail.getJSONArray("waveResults");
        //遍历波数记录
        for (Object waveResult : waveResultArray) {
            JSONObject waveResultObject = (JSONObject) waveResult;

            //封装实体类
            SplatoonCoopWaveDetail splatoonCoopWaveDetail = new SplatoonCoopWaveDetail();
            splatoonCoopWaveDetail.setCoopId(coopId);
            splatoonCoopWaveDetail.setWaveNumber(waveResultObject.getInteger("waveNumber"));
            splatoonCoopWaveDetail.setWaterLevel(waveResultObject.getInteger("waterLevel"));
            if (waveResultObject.getJSONObject("eventWave") != null) {
                splatoonCoopWaveDetail.setEventWaveId(waveResultObject.getJSONObject("eventWave").getString("id"));
                splatoonCoopWaveDetail.setEventWaveName(waveResultObject.getJSONObject("eventWave").getString("name"));
            }
            splatoonCoopWaveDetail.setDeliverNorm(waveResultObject.getInteger("deliverNorm"));
            splatoonCoopWaveDetail.setGoldenPopCount(waveResultObject.getInteger("goldenPopCount"));
            splatoonCoopWaveDetail.setTeamDeliverCount(waveResultObject.getInteger("teamDeliverCount"));
            splatoonCoopWaveDetail.setSpecialWeaponIds(waveResultObject.getJSONArray("specialWeapons")
                    .stream()
                    .map(weapon -> {
                        return  ((JSONObject) weapon).getString("id");
                    })
                    .collect(Collectors.joining(",")));
            splatoonCoopWaveDetail.setSpecialWeaponNames(waveResultObject.getJSONArray("specialWeapons")
                    .stream()
                    .map(weapon -> {
                        return  ((JSONObject) weapon).getString("name");
                    })
                    .collect(Collectors.joining(",")));

            //添加到列表
            waveDetailList.add(splatoonCoopWaveDetail);
        }

        //批量保存
        saveBatch(waveDetailList);
    }
}
