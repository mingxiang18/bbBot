package com.bb.bot.database.splatoon.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.DateUtils;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.database.splatoon.entity.SplatoonBattleUserDetail;
import com.bb.bot.database.splatoon.service.ISplatoonBattleUserDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.bb.bot.database.splatoon.mapper.SplatoonBattleRecordMapper;
import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;
import com.bb.bot.database.splatoon.service.ISplatoonBattleRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 斯普拉遁3对战记录Service业务层处理
 *
 * @author rym
 * @since 2024-04-02
 */
@Service
public class SplatoonBattleRecordServiceImpl extends ServiceImpl<SplatoonBattleRecordMapper,SplatoonBattleRecord> implements ISplatoonBattleRecordService {
    @Autowired
    private SplatoonBattleRecordMapper splatoonBattleRecordMapper;

    @Autowired
    private ISplatoonBattleUserDetailService splatoonBattleUserDetailService;

    @Autowired
    private ResourcesUtils resourcesUtils;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBattleRecordDetail(String userId, JSONObject battleRecord, JSONObject battleDetail) {
        //保存对战数据
        SplatoonBattleRecord splatoonBattleRecord = saveSplatoonBattleRecord(userId, battleRecord, battleDetail);
        //保存对应用户详情数据
        saveSplatoonBattleUserDetail(String.valueOf(splatoonBattleRecord.getId()), battleRecord, battleDetail);
    }

    @Override
    public SplatoonBattleRecord saveSplatoonBattleRecord(String userId, JSONObject battleRecord, JSONObject battleDetail) {
        SplatoonBattleRecord splatoonBattleRecord = new SplatoonBattleRecord();
        splatoonBattleRecord.setAppBattleId(battleRecord.getString("id"));
        splatoonBattleRecord.setUserId(userId);

        //设置对战模式
        JSONObject vsMode = battleRecord.getJSONObject("vsMode");
        splatoonBattleRecord.setVsModeId(vsMode.getString("id"));
        splatoonBattleRecord.setVsModeName(vsMode.getString("mode"));
        if (battleDetail.getJSONObject("bankaraMatch") != null) {
            splatoonBattleRecord.setVsSubMode(battleDetail.getJSONObject("bankaraMatch").getString("mode"));
        }

        //设置对战规则
        JSONObject vsRule = battleRecord.getJSONObject("vsRule");
        splatoonBattleRecord.setVsRuleId(vsRule.getString("id"));
        splatoonBattleRecord.setVsRuleName(vsRule.getString("name"));

        //设置对战地图
        JSONObject vsStage = battleRecord.getJSONObject("vsStage");
        splatoonBattleRecord.setVsStageId(vsStage.getString("id"));
        splatoonBattleRecord.setVsStageName(vsStage.getString("name"));
        resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/battle/stage/" + splatoonBattleRecord.getVsStageId() + ".png",
                battleRecord.getJSONObject("vsStage").getJSONObject("image").getString("url"));

        //设置对战结果
        splatoonBattleRecord.setJudgement(battleRecord.getString("judgement"));
        //设置对战计数
        if (battleRecord.getJSONObject("myTeam") != null
            && battleRecord.getJSONObject("myTeam").getJSONObject("result") != null) {
            JSONObject teamResult = battleRecord.getJSONObject("myTeam").getJSONObject("result");
            //涂地模式设置涂地数，其他模式设置分数
            if (teamResult.getInteger("score") != null) {
                splatoonBattleRecord.setScore(teamResult.getInteger("score"));
            }else if (teamResult.getInteger("paintPoint") != null) {
                splatoonBattleRecord.setScore(teamResult.getInteger("paintPoint"));
            }
        }

        //设置段位,只有部分比赛模式有
        splatoonBattleRecord.setRankCode(battleRecord.getString("udemae"));
        //设置分数变动,只有部分比赛模式有
        if (battleRecord.getJSONObject("bankaraMatch") != null) {
            splatoonBattleRecord.setPointChange(battleRecord.getJSONObject("bankaraMatch").getInteger("earnedUdemaePoint"));
        }
        //设置技术点数,只有部分比赛模式有
        if (battleDetail.getJSONObject("bankaraMatch") != null
                && battleDetail.getJSONObject("bankaraMatch").getJSONObject("bankaraPower") != null
                && battleDetail.getJSONObject("bankaraMatch").getJSONObject("bankaraPower").containsKey("power")) {
            splatoonBattleRecord.setPower(battleDetail.getJSONObject("bankaraMatch").getJSONObject("bankaraPower").getInteger("power"));
        }else if (battleDetail.getJSONObject("leagueMatch") != null
                && battleDetail.getJSONObject("leagueMatch").containsKey("myLeaguePower")) {
            splatoonBattleRecord.setPower(battleDetail.getJSONObject("leagueMatch").getInteger("myLeaguePower"));
        }
        //设置对战时间
        splatoonBattleRecord.setPlayedTime(DateUtils.convertUTCTimeToCNLocalDateTime(battleDetail.getString("playedTime")));

        splatoonBattleRecordMapper.insert(splatoonBattleRecord);
        return splatoonBattleRecord;
    }

    @Override
    public void saveSplatoonBattleUserDetail(String battleId, JSONObject battleRecord, JSONObject battleDetail) {
        List<SplatoonBattleUserDetail> userDetailList = new ArrayList<>();

        //封装己方队伍的对战数据
        if (battleDetail.containsKey("myTeam") && battleDetail.getJSONObject("myTeam").containsKey("players")) {
            JSONObject myTeam = battleDetail.getJSONObject("myTeam");
            JSONArray playerArray = myTeam.getJSONArray("players");
            for (int i = 0; i < playerArray.size(); i++) {
                JSONObject playerData = playerArray.getJSONObject(i);
                //封装用户数据
                SplatoonBattleUserDetail splatoonBattleUserDetail = packageBattleUserRecord(battleId, playerData);
                //设置队伍标识、队伍序号
                splatoonBattleUserDetail.setTeamFlag(1);
                splatoonBattleUserDetail.setTeamOrder(myTeam.getInteger("order"));
                userDetailList.add(splatoonBattleUserDetail);
            }

        }

        //封装其他队伍这场比赛的详细数据
        if (battleDetail.containsKey("otherTeams")) {
            JSONArray otherTeamArray = battleDetail.getJSONArray("otherTeams");
            //遍历其他队伍
            for (int i = 0; i < otherTeamArray.size(); i++) {
                JSONObject otherTeam = otherTeamArray.getJSONObject(i);
                JSONArray playerArray = otherTeam.getJSONArray("players");
                for (int j = 0; j < playerArray.size(); j++) {
                    JSONObject playerData = playerArray.getJSONObject(j);
                    //封装用户数据
                    SplatoonBattleUserDetail splatoonBattleUserDetail = packageBattleUserRecord(battleId, playerData);
                    //设置队伍标识、队伍序号
                    splatoonBattleUserDetail.setTeamFlag(2);
                    splatoonBattleUserDetail.setTeamOrder(otherTeam.getInteger("order"));
                    userDetailList.add(splatoonBattleUserDetail);
                }
            }

        }

        splatoonBattleUserDetailService.saveBatch(userDetailList);
    }

    /**
     * 从接口数据封装用户详情实体类
     */
    private SplatoonBattleUserDetail packageBattleUserRecord(String battleId, JSONObject playerDetail) {
        SplatoonBattleUserDetail splatoonBattleUserDetail = new SplatoonBattleUserDetail();
        splatoonBattleUserDetail.setBattleId(battleId);
        splatoonBattleUserDetail.setMeFlag(playerDetail.getBoolean("isMyself") == true ? 1 : 0);

        //设置用户信息
        splatoonBattleUserDetail.setPlayerId(playerDetail.getString("id"));
        splatoonBattleUserDetail.setPlayerName(playerDetail.getString("name"));
        splatoonBattleUserDetail.setPlayerCode(playerDetail.getString("nameId"));
        splatoonBattleUserDetail.setPlayerTag(playerDetail.getString("byname"));

        //用户背景和徽章
        JSONObject nameplate = playerDetail.getJSONObject("nameplate");
        JSONArray badges = nameplate.getJSONArray("badges");
        //封装用户徽章字符串
        String badgesStr = badges
                .stream()
                .filter(Objects::nonNull)
                .map(badge -> {
                    String id = ((JSONObject) badge).getString("id");
                    //保存图片
                    resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/user/badge/" + id + ".png",
                            ((JSONObject) badge).getJSONObject("image").getString("url"));
                    return id;
                }).collect(Collectors.joining(","));
        splatoonBattleUserDetail.setPlayerBadges(badgesStr);
        //封装用户背景
        JSONObject background = nameplate.getJSONObject("background");
        splatoonBattleUserDetail.setPlayerBackground(background.getString("id"));
        //保存图片
        resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/user/background/" + splatoonBattleUserDetail.getPlayerBackground() + ".png",
                background.getJSONObject("image").getString("url"));

        //用户头部装备
        JSONObject headGear = playerDetail.getJSONObject("headGear");
        splatoonBattleUserDetail.setPlayerHeadGear(headGear.getString("name"));
        //保存图片
        resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/user/gear/" + splatoonBattleUserDetail.getPlayerHeadGear() + ".png",
                headGear.getJSONObject("originalImage").getString("url"));

        //用户衣服装备
        JSONObject clothingGear = playerDetail.getJSONObject("clothingGear");
        splatoonBattleUserDetail.setPlayerClothesGear(clothingGear.getString("name"));
        //保存图片
        resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/user/gear/" + splatoonBattleUserDetail.getPlayerClothesGear() + ".png",
                clothingGear.getJSONObject("originalImage").getString("url"));

        //用户鞋子装备
        JSONObject shoesGear = playerDetail.getJSONObject("shoesGear");
        splatoonBattleUserDetail.setPlayerShoesGear(shoesGear.getString("name"));
        //保存图片
        resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/user/gear/" + splatoonBattleUserDetail.getPlayerShoesGear() + ".png",
                shoesGear.getJSONObject("originalImage").getString("url"));

        //设置武器信息
        JSONObject weapon = playerDetail.getJSONObject("weapon");
        splatoonBattleUserDetail.setWeaponId(weapon.getString("id"));
        splatoonBattleUserDetail.setWeaponName(weapon.getString("name"));
        //保存图片
        resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/weapon/" + splatoonBattleUserDetail.getWeaponId() + ".png",
                weapon.getJSONObject("image").getString("url"));
        //设置副武器信息
        if (weapon.containsKey("subWeapon")) {
            JSONObject subWeapon = weapon.getJSONObject("subWeapon");
            splatoonBattleUserDetail.setWeaponSubWeaponId(subWeapon.getString("id"));
            splatoonBattleUserDetail.setWeaponSubWeaponName(subWeapon.getString("name"));
            //保存图片
            resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/subWeapon/" + splatoonBattleUserDetail.getWeaponSubWeaponId() + ".png",
                    subWeapon.getJSONObject("image").getString("url"));
        }
        //设置特殊武器信息
        if (weapon.containsKey("specialWeapon")) {
            JSONObject specialWeapon = weapon.getJSONObject("specialWeapon");
            splatoonBattleUserDetail.setWeaponSpecialId(specialWeapon.getString("id"));
            splatoonBattleUserDetail.setWeaponSpecialName(specialWeapon.getString("name"));
            //保存图片
            resourcesUtils.getOrAddStaticResourceFromNet("nso_splatoon/specialWeapon/" + splatoonBattleUserDetail.getWeaponSpecialId() + ".png",
                    specialWeapon.getJSONObject("image").getString("url"));
        }

        //设置涂地数
        splatoonBattleUserDetail.setPaintCount(playerDetail.getInteger("paint"));
        //设置击倒、助攻、死亡数
        JSONObject result = playerDetail.getJSONObject("result");
        if (result != null) {
            splatoonBattleUserDetail.setKillCount(result.getInteger("kill"));
            splatoonBattleUserDetail.setDeathCount(result.getInteger("death"));
            splatoonBattleUserDetail.setAssistCount(result.getInteger("assist"));
            splatoonBattleUserDetail.setSpecialCount(result.getInteger("special"));
        }

        return splatoonBattleUserDetail;
    }
}
