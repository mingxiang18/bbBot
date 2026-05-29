package com.bb.bot.database.splatoon.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;
import com.bb.bot.database.splatoon.entity.SplatoonBattleUserDetail;
import com.bb.bot.database.splatoon.mapper.SplatoonBattleRecordMapper;
import com.bb.bot.database.splatoon.service.ISplatoonBattleUserDetailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link SplatoonBattleRecordServiceImpl} 单元测试。
 *
 * <p>覆盖 T6.1 重构（JSON 链式取值改局部缓存 + 抽取 {@code saveGearResource}）后的行为等价性：
 * 喂样本 battle JSON 断言落库字段；mock {@link ResourcesUtils} 断言资源路径/URL；null 分支。
 * 不引入 Spring 容器，直接 new + ReflectionTestUtils 注入 mock 依赖。
 */
class SplatoonBattleRecordServiceImplTest {

    private SplatoonBattleRecordServiceImpl service;
    private SplatoonBattleRecordMapper mapper;
    private ISplatoonBattleUserDetailService userDetailService;
    private ResourcesUtils resourcesUtils;

    @BeforeEach
    void setUp() {
        service = new SplatoonBattleRecordServiceImpl();
        mapper = mock(SplatoonBattleRecordMapper.class);
        userDetailService = mock(ISplatoonBattleUserDetailService.class);
        resourcesUtils = mock(ResourcesUtils.class);
        ReflectionTestUtils.setField(service, "splatoonBattleRecordMapper", mapper);
        ReflectionTestUtils.setField(service, "splatoonBattleUserDetailService", userDetailService);
        ReflectionTestUtils.setField(service, "resourcesUtils", resourcesUtils);
    }

    // ---------------------------------------------------------------------
    // saveSplatoonBattleRecord：落库字段 + 地图资源
    // ---------------------------------------------------------------------

    @Test
    void saveSplatoonBattleRecord_bankaraMatch_mapsAllFields() {
        JSONObject battleRecord = baseRecord();
        battleRecord.fluentPut("myTeam", obj().fluentPut("result", obj().fluentPut("score", 100)))
                .fluentPut("bankaraMatch", obj().fluentPut("earnedUdemaePoint", 8));

        JSONObject battleDetail = baseDetail();
        battleDetail.fluentPut("knockout", "NEITHER")
                .fluentPut("bankaraMatch", obj()
                        .fluentPut("mode", "OPEN")
                        .fluentPut("bankaraPower", obj().fluentPut("power", 1850)));

        SplatoonBattleRecord record = service.saveSplatoonBattleRecord("user-1", battleRecord, battleDetail);

        assertThat(record.getAppBattleId()).isEqualTo("battle-id");
        assertThat(record.getUserId()).isEqualTo("user-1");
        assertThat(record.getVsModeId()).isEqualTo("m");
        assertThat(record.getVsModeName()).isEqualTo("REGULAR");
        assertThat(record.getVsSubMode()).isEqualTo("OPEN");
        assertThat(record.getVsRuleId()).isEqualTo("r");
        assertThat(record.getVsRuleName()).isEqualTo("rule");
        assertThat(record.getVsStageId()).isEqualTo("s");
        assertThat(record.getVsStageName()).isEqualTo("stage");
        assertThat(record.getJudgement()).isEqualTo("LOSE");
        assertThat(record.getScore()).isEqualTo(100);
        assertThat(record.getRankCode()).isEqualTo("A");
        assertThat(record.getPointChange()).isEqualTo(8);
        assertThat(record.getPower()).isEqualTo(1850);
        assertThat(record.getDuration()).isEqualTo(200);
        // knockout=NEITHER 不记
        assertThat(record.getKnockout()).isNull();

        // 地图资源：路径用 vsStageId，url 取自缓存后的 vsStage.image.url
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/battle/stage/s.png", "https://img/s.png");
        verify(mapper).insert(record);
    }

    @Test
    void saveSplatoonBattleRecord_paintPointWhenNoScore() {
        JSONObject battleRecord = baseRecord();
        battleRecord.fluentPut("myTeam", obj().fluentPut("result", obj().fluentPut("paintPoint", 1234)));

        SplatoonBattleRecord record = service.saveSplatoonBattleRecord("u", battleRecord, baseDetail());

        assertThat(record.getScore()).isEqualTo(1234);
    }

    @Test
    void saveSplatoonBattleRecord_leaguePowerWhenNoBankara() {
        JSONObject battleDetail = baseDetail();
        battleDetail.fluentPut("leagueMatch", obj().fluentPut("myLeaguePower", 2100));

        SplatoonBattleRecord record = service.saveSplatoonBattleRecord("u", baseRecord(), battleDetail);

        assertThat(record.getPower()).isEqualTo(2100);
    }

    @Test
    void saveSplatoonBattleRecord_realKnockoutRecorded() {
        JSONObject battleDetail = baseDetail();
        battleDetail.fluentPut("knockout", "WIN");

        SplatoonBattleRecord record = service.saveSplatoonBattleRecord("u", baseRecord(), battleDetail);

        assertThat(record.getKnockout()).isEqualTo("WIN");
    }

    @Test
    void saveSplatoonBattleRecord_scoreText_paintRatioAndCount() {
        JSONObject battleDetail = baseDetail();
        // myTeam 占地：paintRatio 0.5123 -> "51.2"
        battleDetail.fluentPut("myTeam", obj().fluentPut("result", obj().fluentPut("paintRatio", 0.5123)));
        // otherTeams[0] 真格：score 计数
        JSONArray otherTeams = new JSONArray();
        otherTeams.add(obj().fluentPut("result", obj().fluentPut("score", 3)));
        battleDetail.fluentPut("otherTeams", otherTeams);

        SplatoonBattleRecord record = service.saveSplatoonBattleRecord("u", baseRecord(), battleDetail);

        assertThat(record.getMyScore()).isEqualTo("51.2");
        assertThat(record.getOtherScore()).isEqualTo("3");
    }

    @Test
    void saveSplatoonBattleRecord_nullBranches_noNpe() {
        // 仅必填字段，其余可空分支全部为 null：myTeam / bankaraMatch / leagueMatch / awards / scoreText
        SplatoonBattleRecord record = service.saveSplatoonBattleRecord("u", baseRecord(), baseDetail());

        assertThat(record.getScore()).isNull();
        assertThat(record.getPointChange()).isNull();
        assertThat(record.getPower()).isNull();
        assertThat(record.getVsSubMode()).isNull();
        assertThat(record.getMyScore()).isNull();
        assertThat(record.getOtherScore()).isNull();
        assertThat(record.getAwards()).isNull();
    }

    // ---------------------------------------------------------------------
    // saveSplatoonBattleUserDetail -> packageBattleUserRecord：装备资源 saveGearResource
    // ---------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void saveSplatoonBattleUserDetail_gearResources_pathAndUrl() {
        JSONArray players = new JSONArray();
        players.add(samplePlayer());
        JSONObject myTeam = obj().fluentPut("order", 1).fluentPut("players", players);
        JSONObject battleDetail = obj().fluentPut("myTeam", myTeam);

        service.saveSplatoonBattleUserDetail("battle-1", new JSONObject(), battleDetail);

        // saveGearResource：头/衣/鞋 三件,路径用装备 name,url 取自 originalImage.url
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/user/gear/帽子A.png", "https://img/head.png");
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/user/gear/衣服B.png", "https://img/cloth.png");
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/user/gear/鞋子C.png", "https://img/shoes.png");
        // 武器资源同样落库
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/weapon/weapon-1.png", "https://img/weapon.png");

        ArgumentCaptor<Collection<SplatoonBattleUserDetail>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userDetailService).saveBatch(captor.capture());
        List<SplatoonBattleUserDetail> saved = List.copyOf(captor.getValue());
        assertThat(saved).hasSize(1);
        SplatoonBattleUserDetail d = saved.get(0);
        assertThat(d.getBattleId()).isEqualTo("battle-1");
        assertThat(d.getTeamFlag()).isEqualTo(1);
        assertThat(d.getTeamOrder()).isEqualTo(1);
        assertThat(d.getPlayerHeadGear()).isEqualTo("帽子A");
        assertThat(d.getPlayerClothesGear()).isEqualTo("衣服B");
        assertThat(d.getPlayerShoesGear()).isEqualTo("鞋子C");
        assertThat(d.getGearPowers()).isEqualTo("主头 主衣 主鞋");
        assertThat(d.getWeaponName()).isEqualTo("武器1");
        assertThat(d.getKillCount()).isEqualTo(5);
    }

    @Test
    void saveSplatoonBattleUserDetail_noMyTeam_noGearResource() {
        // myTeam 缺失：走 null 分支,不触发任何装备资源保存
        service.saveSplatoonBattleUserDetail("battle-1", new JSONObject(), new JSONObject());

        verifyNoInteractions(resourcesUtils);
        verify(userDetailService).saveBatch(any());
    }

    @Test
    void saveGearResource_nullGear_noInteraction() {
        ReflectionTestUtils.invokeMethod(service, "saveGearResource", "anyName", (JSONObject) null);
        verifyNoInteractions(resourcesUtils);
    }

    @Test
    void saveGearResource_nonNull_savesPath() {
        JSONObject gear = obj().fluentPut("originalImage", obj().fluentPut("url", "https://img/g.png"));
        ReflectionTestUtils.invokeMethod(service, "saveGearResource", "GearName", gear);
        verify(resourcesUtils, times(1)).getOrAddStaticResourceFromNet(
                eq("nso_splatoon/user/gear/GearName.png"), eq("https://img/g.png"));
        verify(resourcesUtils, never()).getOrAddStaticResourceFromNet(eq("other"), any());
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private static JSONObject obj() {
        return new JSONObject();
    }

    private JSONObject baseRecord() {
        return obj()
                .fluentPut("id", "battle-id")
                .fluentPut("vsMode", obj().fluentPut("id", "m").fluentPut("mode", "REGULAR"))
                .fluentPut("vsRule", obj().fluentPut("id", "r").fluentPut("name", "rule"))
                .fluentPut("vsStage", obj().fluentPut("id", "s").fluentPut("name", "stage")
                        .fluentPut("image", obj().fluentPut("url", "https://img/s.png")))
                .fluentPut("judgement", "LOSE")
                .fluentPut("udemae", "A");
    }

    private JSONObject baseDetail() {
        return obj()
                .fluentPut("playedTime", "2024-04-02T10:00:00Z")
                .fluentPut("duration", 200);
    }

    private JSONObject samplePlayer() {
        return obj()
                .fluentPut("isMyself", true)
                .fluentPut("id", "p1")
                .fluentPut("name", "玩家")
                .fluentPut("nameId", "1234")
                .fluentPut("byname", "称号")
                .fluentPut("nameplate", obj()
                        .fluentPut("badges", new JSONArray())
                        .fluentPut("background", obj().fluentPut("id", "bg-1")
                                .fluentPut("image", obj().fluentPut("url", "https://img/bg.png"))))
                .fluentPut("headGear", obj().fluentPut("name", "帽子A")
                        .fluentPut("originalImage", obj().fluentPut("url", "https://img/head.png"))
                        .fluentPut("primaryGearPower", obj().fluentPut("name", "主头")))
                .fluentPut("clothingGear", obj().fluentPut("name", "衣服B")
                        .fluentPut("originalImage", obj().fluentPut("url", "https://img/cloth.png"))
                        .fluentPut("primaryGearPower", obj().fluentPut("name", "主衣")))
                .fluentPut("shoesGear", obj().fluentPut("name", "鞋子C")
                        .fluentPut("originalImage", obj().fluentPut("url", "https://img/shoes.png"))
                        .fluentPut("primaryGearPower", obj().fluentPut("name", "主鞋")))
                .fluentPut("weapon", obj().fluentPut("id", "weapon-1").fluentPut("name", "武器1")
                        .fluentPut("image", obj().fluentPut("url", "https://img/weapon.png")))
                .fluentPut("paint", 800)
                .fluentPut("result", obj().fluentPut("kill", 5).fluentPut("death", 2)
                        .fluentPut("assist", 1).fluentPut("special", 3));
    }
}
