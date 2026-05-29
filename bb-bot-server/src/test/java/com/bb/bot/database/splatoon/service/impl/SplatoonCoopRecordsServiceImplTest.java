package com.bb.bot.database.splatoon.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.mapper.SplatoonCoopRecordsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link SplatoonCoopRecordsServiceImpl} 单元测试。
 *
 * <p>覆盖 T6.2 重构（JSON 链式取值改局部缓存 + 抽取 {@code saveWeaponResource}）后的行为等价性：
 * 喂样本 coop JSON 断言落库字段；mock {@link ResourcesUtils} 断言资源路径/URL；null 分支。
 * 不引入 Spring 容器，直接 new + ReflectionTestUtils 注入 mock 依赖。
 */
class SplatoonCoopRecordsServiceImplTest {

    private SplatoonCoopRecordsServiceImpl service;
    private SplatoonCoopRecordsMapper mapper;
    private ResourcesUtils resourcesUtils;

    @BeforeEach
    void setUp() {
        service = new SplatoonCoopRecordsServiceImpl();
        mapper = mock(SplatoonCoopRecordsMapper.class);
        resourcesUtils = mock(ResourcesUtils.class);
        ReflectionTestUtils.setField(service, "splatoonCoopRecordsMapper", mapper);
        ReflectionTestUtils.setField(service, "resourcesUtils", resourcesUtils);
    }

    // ---------------------------------------------------------------------
    // saveSplatoonCoopRecord：完整样本，落库字段 + 地图/Boss/武器资源
    // ---------------------------------------------------------------------

    @Test
    void saveSplatoonCoopRecord_fullSample_mapsAllFields() {
        JSONObject coopRecord = fullRecord();
        JSONObject coopDetail = fullDetail();

        SplatoonCoopRecord record = service.saveSplatoonCoopRecord("user-1", coopRecord, coopDetail);

        // 基本字段
        assertThat(record.getAppCoopId()).isEqualTo("coop-id");
        assertThat(record.getUserId()).isEqualTo("user-1");
        assertThat(record.getRule()).isEqualTo("REGULAR");
        assertThat(record.getCoopStageId()).isEqualTo("stage-1");
        assertThat(record.getCoopStageName()).isEqualTo("地图A");
        // dangerRate 0.625 * 100 = 62
        assertThat(record.getDangerRate()).isEqualTo("62");
        assertThat(record.getAfterGradeId()).isEqualTo("grade-1");
        assertThat(record.getAfterGradeName()).isEqualTo("熟练");
        assertThat(record.getAfterGradePoint()).isEqualTo(120);
        assertThat(record.getGradePointDiff()).isEqualTo("UP");
        assertThat(record.getResultWave()).isEqualTo(0);

        // 红蛋: myResult.deliverCount(10) + members(3+7) = 20
        assertThat(record.getTeamRedCount()).isEqualTo(20);
        // 金蛋: waveResults teamDeliverCount 28 + 24 + 31 = 83
        assertThat(record.getTeamGlodenCount()).isEqualTo(83);

        // boss
        assertThat(record.getBossId()).isEqualTo("boss-1");
        assertThat(record.getBossName()).isEqualTo("头目A");
        assertThat(record.getBossDefeatFlag()).isTrue();
        assertThat(record.getGoldScale()).isEqualTo(1);
        assertThat(record.getSilverScale()).isEqualTo(2);
        assertThat(record.getBronzeScale()).isEqualTo(3);

        // 武器名
        assertThat(record.getWeapon1()).isEqualTo("武器1");
        assertThat(record.getWeapon2()).isEqualTo("武器2");
        assertThat(record.getWeapon3()).isEqualTo("武器3");
        assertThat(record.getWeapon4()).isEqualTo("武器4");

        // 得分
        assertThat(record.getJobScore()).isEqualTo(50);
        assertThat(record.getJobBonus()).isEqualTo(60);
        assertThat(record.getSmellMeter()).isEqualTo(4);

        // wave 概要
        assertThat(record.getWaveInfo()).isEqualTo("W1 28·W2 24·W3 31");

        // 资源：地图
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/coop/stage/地图A.png", "https://img/stage.png");
        // 资源：boss 立绘按 bossId
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/coop/boss/boss-1.png", "https://img/boss.png");
        // 资源：四把武器,路径用 weapon name,url 取自 image.url
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/coop/weapon/武器1.png", "https://img/w1.png");
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/coop/weapon/武器2.png", "https://img/w2.png");
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/coop/weapon/武器3.png", "https://img/w3.png");
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/coop/weapon/武器4.png", "https://img/w4.png");

        verify(mapper).insert(record);
    }

    // ---------------------------------------------------------------------
    // null / 空分支：afterGrade / myResult / bossResult 缺失,武器仅 1 把
    // ---------------------------------------------------------------------

    @Test
    void saveSplatoonCoopRecord_nullBranches_noNpeAndNoExtraResource() {
        JSONObject coopRecord = obj()
                .fluentPut("id", "coop-id")
                .fluentPut("gradePointDiff", "KEEP")
                // myResult 缺失 -> 红蛋分支跳过
                .fluentPut("waveResults", new JSONArray())   // 空 -> 金蛋/wave 概要跳过
                // bossResult 缺失 -> boss 分支跳过
                .fluentPut("weapons", weapons(1));            // 仅 1 把武器
        JSONObject coopDetail = obj()
                .fluentPut("rule", "REGULAR")
                .fluentPut("playedTime", "2024-04-02T10:00:00Z")
                .fluentPut("coopStage", obj().fluentPut("id", "stage-1").fluentPut("name", "地图A")
                        .fluentPut("image", obj().fluentPut("url", "https://img/stage.png")))
                .fluentPut("dangerRate", new java.math.BigDecimal("0.0"))
                // afterGrade 缺失
                .fluentPut("afterGradePoint", 0)
                .fluentPut("resultWave", 1)
                .fluentPut("jobScore", 1)
                .fluentPut("jobBonus", 0)
                .fluentPut("smellMeter", 0);

        SplatoonCoopRecord record = service.saveSplatoonCoopRecord("u", coopRecord, coopDetail);

        // afterGrade 缺失 -> id/name 为 null
        assertThat(record.getAfterGradeId()).isNull();
        assertThat(record.getAfterGradeName()).isNull();
        // myResult 缺失 -> 红蛋不计
        assertThat(record.getTeamRedCount()).isNull();
        // waveResults 空 -> 金蛋不计,wave 概要不设
        assertThat(record.getTeamGlodenCount()).isNull();
        assertThat(record.getWaveInfo()).isNull();
        // bossResult 缺失 -> boss 字段/鳞片为 null
        assertThat(record.getBossId()).isNull();
        assertThat(record.getGoldScale()).isNull();
        // 仅 1 把武器
        assertThat(record.getWeapon1()).isEqualTo("武器1");
        assertThat(record.getWeapon2()).isNull();

        // 资源：仅地图 + 武器1,无 boss,无武器2/3/4
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/coop/stage/地图A.png", "https://img/stage.png");
        verify(resourcesUtils).getOrAddStaticResourceFromNet(
                "nso_splatoon/coop/weapon/武器1.png", "https://img/w1.png");
        verify(resourcesUtils, never()).getOrAddStaticResourceFromNet(
                eq("nso_splatoon/coop/weapon/武器2.png"), any());
        verify(resourcesUtils, never()).getOrAddStaticResourceFromNet(
                eq("nso_splatoon/coop/boss/boss-1.png"), any());

        verify(mapper).insert(record);
    }

    @Test
    void saveSplatoonCoopRecord_bossWithoutImage_noBossResource() {
        JSONObject coopRecord = fullRecord();
        // boss 无 image 节点 -> 不下载立绘,但 boss 字段照常落库
        coopRecord.getJSONObject("bossResult").put("boss",
                obj().fluentPut("id", "boss-1").fluentPut("name", "头目A"));
        JSONObject coopDetail = fullDetail();

        SplatoonCoopRecord record = service.saveSplatoonCoopRecord("u", coopRecord, coopDetail);

        assertThat(record.getBossId()).isEqualTo("boss-1");
        assertThat(record.getBossName()).isEqualTo("头目A");
        verify(resourcesUtils, never()).getOrAddStaticResourceFromNet(
                eq("nso_splatoon/coop/boss/boss-1.png"), any());
    }

    // ---------------------------------------------------------------------
    // saveWeaponResource：私有方法直测(null 分支 + 落库)
    // ---------------------------------------------------------------------

    @Test
    void saveWeaponResource_nullWeapon_noInteraction() {
        ReflectionTestUtils.invokeMethod(service, "saveWeaponResource", "anyName", (JSONObject) null);
        verify(resourcesUtils, never()).getOrAddStaticResourceFromNet(any(), any());
    }

    @Test
    void saveWeaponResource_nonNull_savesPath() {
        JSONObject weapon = obj().fluentPut("image", obj().fluentPut("url", "https://img/w.png"));
        ReflectionTestUtils.invokeMethod(service, "saveWeaponResource", "WeaponName", weapon);
        verify(resourcesUtils, times(1)).getOrAddStaticResourceFromNet(
                eq("nso_splatoon/coop/weapon/WeaponName.png"), eq("https://img/w.png"));
    }

    // ---------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------

    private static JSONObject obj() {
        return new JSONObject();
    }

    private JSONArray weapons(int count) {
        JSONArray arr = new JSONArray();
        for (int i = 1; i <= count; i++) {
            arr.add(obj().fluentPut("name", "武器" + i)
                    .fluentPut("image", obj().fluentPut("url", "https://img/w" + i + ".png")));
        }
        return arr;
    }

    private JSONObject fullRecord() {
        JSONArray memberResults = new JSONArray();
        memberResults.add(obj().fluentPut("deliverCount", 3));
        memberResults.add(obj().fluentPut("deliverCount", 7));

        JSONArray waveResults = new JSONArray();
        waveResults.add(obj().fluentPut("waveNumber", 1).fluentPut("teamDeliverCount", 28));
        waveResults.add(obj().fluentPut("waveNumber", 2).fluentPut("teamDeliverCount", 24));
        waveResults.add(obj().fluentPut("waveNumber", 3).fluentPut("teamDeliverCount", 31));

        return obj()
                .fluentPut("id", "coop-id")
                .fluentPut("gradePointDiff", "UP")
                .fluentPut("myResult", obj().fluentPut("deliverCount", 10))
                .fluentPut("memberResults", memberResults)
                .fluentPut("waveResults", waveResults)
                .fluentPut("bossResult", obj()
                        .fluentPut("hasDefeatBoss", true)
                        .fluentPut("boss", obj().fluentPut("id", "boss-1").fluentPut("name", "头目A")
                                .fluentPut("image", obj().fluentPut("url", "https://img/boss.png"))))
                .fluentPut("weapons", weapons(4));
    }

    private JSONObject fullDetail() {
        return obj()
                .fluentPut("rule", "REGULAR")
                .fluentPut("playedTime", "2024-04-02T10:00:00Z")
                .fluentPut("coopStage", obj().fluentPut("id", "stage-1").fluentPut("name", "地图A")
                        .fluentPut("image", obj().fluentPut("url", "https://img/stage.png")))
                .fluentPut("dangerRate", new java.math.BigDecimal("0.625"))
                .fluentPut("afterGrade", obj().fluentPut("id", "grade-1").fluentPut("name", "熟练"))
                .fluentPut("afterGradePoint", 120)
                .fluentPut("resultWave", 0)
                .fluentPut("scale", obj().fluentPut("gold", 1).fluentPut("silver", 2).fluentPut("bronze", 3))
                .fluentPut("jobScore", 50)
                .fluentPut("jobBonus", 60)
                .fluentPut("smellMeter", 4);
    }
}
