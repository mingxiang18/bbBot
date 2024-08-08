package com.bb.bot.database.splatoon.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.database.splatoon.entity.SplatoonCoopUserDetail;
import com.bb.bot.database.splatoon.mapper.SplatoonCoopUserDetailMapper;
import com.bb.bot.database.splatoon.service.ISplatoonCoopUserDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 斯普拉遁3玩家打工详情Service业务层处理
 *
 * @author rym
 * @date 2024-02-01
 */
@Service
public class SplatoonCoopUserDetailServiceImpl extends ServiceImpl<SplatoonCoopUserDetailMapper, SplatoonCoopUserDetail> implements ISplatoonCoopUserDetailService {
    @Autowired
    private SplatoonCoopUserDetailMapper splatoonCoopUserDetailMapper;

    @Autowired
    private ResourcesUtils resourcesUtils;


    @Override
    public void saveSplatoonCoopUserDetail(String coopId, JSONObject coopRecord, JSONObject coopDetail) {
        List<SplatoonCoopUserDetail> userDetailList = new ArrayList<>();

        //封装用户自己这场工的详细数据
        if (coopDetail.getJSONObject("myResult") != null) {
            SplatoonCoopUserDetail splatoonCoopUserDetail = packageCoopUserRecord(coopId, coopDetail.getJSONObject("myResult"));
            splatoonCoopUserDetail.setMeFlag(true);
            userDetailList.add(splatoonCoopUserDetail);
        }

        //封装其他玩家这场工的详细数据
        if (!CollectionUtils.isEmpty(coopDetail.getJSONArray("memberResults"))) {
            for (Object memberResult : coopDetail.getJSONArray("memberResults")) {
                SplatoonCoopUserDetail splatoonCoopUserDetail = packageCoopUserRecord(coopId, (JSONObject) memberResult);
                splatoonCoopUserDetail.setMeFlag(false);
                userDetailList.add(splatoonCoopUserDetail);
            }
        }

        saveBatch(userDetailList);
    }

    /**
     * 从接口数据封装用户详情实体类
     */
    public SplatoonCoopUserDetail packageCoopUserRecord(String coopId, JSONObject coopUserDetail) {
        SplatoonCoopUserDetail splatoonCoopUserDetail = new SplatoonCoopUserDetail();
        splatoonCoopUserDetail.setCoopId(coopId);
        splatoonCoopUserDetail.setPlayerId(coopUserDetail.getJSONObject("player").getString("id"));
        splatoonCoopUserDetail.setPlayerName(coopUserDetail.getJSONObject("player").getString("name"));
        splatoonCoopUserDetail.setPlayerCode(coopUserDetail.getJSONObject("player").getString("nameId"));
        splatoonCoopUserDetail.setPlayerTag(coopUserDetail.getJSONObject("player").getString("byname"));
        splatoonCoopUserDetail.setPlayerClothesName(coopUserDetail.getJSONObject("player").getJSONObject("uniform").getString("name"));
        splatoonCoopUserDetail.setWeapons(coopUserDetail.getJSONArray("weapons")
                .stream()
                .map(weapon -> {
                    resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/coop/weapon/" + ((JSONObject) weapon).getString("name") + ".png",
                            ((JSONObject) weapon).getJSONObject("image").getString("url"));
                    return  ((JSONObject) weapon).getString("name");
                })
                .collect(Collectors.joining(",")));
        if (coopUserDetail.getJSONObject("specialWeapon") != null) {
            splatoonCoopUserDetail.setSpecialWeaponId(coopUserDetail.getJSONObject("specialWeapon").getString("weaponId"));
            splatoonCoopUserDetail.setSpecialWeaponName(coopUserDetail.getJSONObject("specialWeapon").getString("name"));
            resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/coop/specialWeapon/" + splatoonCoopUserDetail.getSpecialWeaponName() + ".png",
                    coopUserDetail.getJSONObject("specialWeapon").getJSONObject("image").getString("url"));
        }
        splatoonCoopUserDetail.setDefeatEnemyCount(coopUserDetail.getInteger("defeatEnemyCount"));
        splatoonCoopUserDetail.setDeliverRedCount(coopUserDetail.getInteger("deliverCount"));
        splatoonCoopUserDetail.setDeliverGlodenCount(coopUserDetail.getInteger("goldenDeliverCount"));
        splatoonCoopUserDetail.setAssistGlodenCount(coopUserDetail.getInteger("goldenAssistCount"));
        splatoonCoopUserDetail.setRescueCount(coopUserDetail.getInteger("rescueCount"));
        splatoonCoopUserDetail.setRescuedCount(coopUserDetail.getInteger("rescuedCount"));
        return splatoonCoopUserDetail;
    }
}
