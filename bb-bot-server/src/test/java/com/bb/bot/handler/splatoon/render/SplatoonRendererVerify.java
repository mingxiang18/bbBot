package com.bb.bot.handler.splatoon.render;

import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.common.util.SpringUtils;
import com.bb.bot.common.util.fileClient.LocalFileClientApiImpl;
import com.bb.bot.config.FilePathConfig;
import com.bb.bot.database.splatoon.entity.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用「真实字段值 + 边界数据」本地渲染 4 张图,检查无错位/重叠/换行/缺图。
 * 真实风险点都覆盖:长玩家名、长装备技能名、power 蛋上千、KO=NEITHER、占地 paintRatio、
 * 打工通关/失败、有/无 boss。资源用本地 static 真实图标。
 */
public class SplatoonRendererVerify {

    static final Path STATIC = Paths.get("bb-bot-server/src/main/resources/static/");
    static final Path OUT = Paths.get("bb-bot-server/target/render-verify/");

    // 本地真实存在的资源 id
    static final String[] W = {"V2VhcG9uLTA=", "V2VhcG9uLTE=", "V2VhcG9uLTEw", "V2VhcG9uLTEwMA==",
            "V2VhcG9uLTEwMDA=", "V2VhcG9uLTEwMDE=", "V2VhcG9uLTEwMQ==", "V2VhcG9uLTEwMTA="};
    static final String[] SP = {"U3BlY2lhbFdlYXBvbi00", "U3BlY2lhbFdlYXBvbi01", "U3BlY2lhbFdlYXBvbi02", "U3BlY2lhbFdlYXBvbi03"};
    static final String STAGE = "VnNTdGFnZS00";
    static final String[] CW = {"14式竹筒槍‧甲", "24式可替換傘‧甲", "H3捲管槍", "L3捲管槍"};
    static final String COOPSTAGE = "新卷堡";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        Files.createDirectories(STATIC.resolve("tmp"));

        FilePathConfig cfg = new FilePathConfig();
        set(cfg, "filePath", STATIC.toAbsolutePath() + "/");
        // SpringUtils.beanFactory 动态代理:getBean(FilePathConfig.class) 返回 cfg
        Object bf = Proxy.newProxyInstance(SplatoonRendererVerify.class.getClassLoader(),
                new Class<?>[]{Class.forName("org.springframework.beans.factory.config.ConfigurableListableBeanFactory")},
                (InvocationHandler) (proxy, m, a) -> "getBean".equals(m.getName()) && a != null && a.length == 1 && a[0] == FilePathConfig.class ? cfg
                        : defaultReturn(m));
        set(SpringUtils.class, "beanFactory", bf);

        ResourcesUtils ru = new ResourcesUtils();
        set(ru, "filePathConfig", cfg);
        set(ru, "fileClientApi", new LocalFileClientApiImpl());

        SplatoonHtmlRenderer r = new SplatoonHtmlRenderer();
        set(r, "resourcesUtils", ru);

        copy(r.renderBattleList(battleRecords(), battleDetails()), "verify-battle-list.png");
        copy(r.renderBattleDetail(battleRecords().get(0), detailsFor(1L)), "verify-battle-detail.png");
        copy(r.renderCoopList(coopRecords(), coopDetails()), "verify-coop-list.png");
        copy(r.renderCoopDetail(coopRecords().get(0), coopDetailsFor(1L), waves(1L), enemies(1L)), "verify-coop-detail.png");
        System.out.println("OUT: " + OUT.toAbsolutePath());
    }

    /* ---- 对战 ---- */

    static List<SplatoonBattleRecord> battleRecords() {
        List<SplatoonBattleRecord> list = new ArrayList<>();
        list.add(battle(1L, "VnNNb2RlLTUx", "VnNSdWxlLTE=", "区域控制", "真鲭跳台", "WIN", "WIN", "99", "73", "S+0", 8, null, 317, "计数推进 No.1,刷新最佳计数,涂墨点数 No.1"));
        list.add(battle(2L, "VnNNb2RlLTE=", null, "占地比赛", "鱼板拼盘", "LOSE", null, "47.7", "52.3", null, null, null, 180, null));
        list.add(battle(3L, "VnNNb2RlLTM=", "VnNSdWxlLTM=", "真格鱼虎", "金枪鱼美术馆超长地图名字测试", "WIN", null, "胜利", null, null, null, 2483, 245, null));
        list.add(battle(4L, "VnNNb2RlLTUx", "VnNSdWxlLTQ=", "真格塔楼", "海女美术大学", "WIN", null, "100", "88", "S+1", 12, null, 228, null));
        list.add(battle(5L, "VnNNb2RlLTE=", null, "占地比赛", "古鲸鱼工厂", "LOSE", null, "45.1", "54.9", null, null, null, 180, null));
        return list;
    }

    static SplatoonBattleRecord battle(Long id, String modeId, String ruleId, String ruleName, String stage,
                                       String judge, String ko, String my, String other, String rank, Integer pc, Integer power, Integer dur, String awards) {
        SplatoonBattleRecord r = new SplatoonBattleRecord();
        r.setId(id); r.setVsModeId(modeId); r.setVsRuleId(ruleId); r.setVsRuleName(ruleName);
        r.setVsStageId(STAGE); r.setVsStageName(stage); r.setJudgement(judge); r.setKnockout(ko);
        r.setMyScore(my); r.setOtherScore(other); r.setRankCode(rank); r.setPointChange(pc); r.setPower(power);
        r.setDuration(dur); r.setAwards(awards); r.setPlayedTime(LocalDateTime.now());
        return r;
    }

    static List<SplatoonBattleUserDetail> battleDetails() {
        List<SplatoonBattleUserDetail> all = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            all.addAll(detailsFor(id));
        }
        return all;
    }

    static List<SplatoonBattleUserDetail> detailsFor(long battleId) {
        List<SplatoonBattleUserDetail> ps = new ArrayList<>();
        String[] names = {"我", "あおいとてもながい", "Koharu", "ナギ", "Rio", "つばさ", "Leonardo长名", "ゆうき"};
        // 真实长技能名(测换行)
        String[] gears = {"提升鱿鱼冲刺速度 减少游泳墨水消耗 提升人类移动速度", "回墨提升", "人速提升 防御提升", "主武器电池"};
        for (int i = 0; i < 8; i++) {
            SplatoonBattleUserDetail p = new SplatoonBattleUserDetail();
            p.setBattleId(String.valueOf(battleId));
            p.setTeamFlag(i < 4 ? 1 : 2);
            p.setMeFlag(i == 0 ? 1 : 0);
            p.setPlayerName(names[i]);
            p.setWeaponId(W[i]);
            p.setWeaponName("武器名称" + i);
            p.setWeaponSpecialId(SP[i % 4]);
            p.setKillCount(12 - i); p.setAssistCount(i % 4); p.setDeathCount(3 + i); p.setSpecialCount(i % 5);
            p.setPaintCount(1280 - i * 70);
            p.setGearPowers(gears[i % 4]);
            ps.add(p);
        }
        return ps;
    }

    /* ---- 打工 ---- */

    static List<SplatoonCoopRecord> coopRecords() {
        List<SplatoonCoopRecord> list = new ArrayList<>();
        list.add(coop(1L, "新卷堡", "BIG_RUN", "241", "达人", "UP", 0, 33, 4521, "henggang", "横纲鲑", true, 3, 5, 12, "W1 41·W2 38·W3 33", 127, 0, 1));
        list.add(coop(2L, "时不知鲑烟熏工房", "REGULAR", "120", "达人", "DOWN", 2, 18, 2100, null, null, null, 0, 0, 0, "W1 26·W2 38", 64, 0, 0));
        list.add(coop(3L, "塔拉波特購物公園", "TEAM_CONTEST", "220", "达人", "UP", 0, 26, 3200, null, null, null, 0, 0, 0, "W1 24·W2 26·W3 23", 102, 0, 2));
        list.add(coop(4L, "新卷堡", "BIG_RUN", "333", "传说", "UP", 0, 35, 5100, "chenlong", "辰龙", true, 5, 8, 15, "W1 31·W2 34·W3 33", 188, 50, 4));
        list.add(coop(5L, "时不知鲑烟熏工房", "REGULAR", "90", "胜", "DOWN", 1, 9, 980, null, null, null, 0, 0, 0, "W1 28", 28, 0, 0));
        return list;
    }

    static SplatoonCoopRecord coop(Long id, String stage, String rule, String danger, String grade, String diff,
                                   int resultWave, int teamGold, int teamRed, String bossId, String bossName, Boolean bossDefeat,
                                   int g, int s, int b, String waveInfo, Integer jobScore, Integer jobBonus, Integer smell) {
        SplatoonCoopRecord r = new SplatoonCoopRecord();
        r.setId(id); r.setCoopStageName(stage); r.setRule(rule); r.setDangerRate(danger);
        r.setAfterGradeName(grade); r.setGradePointDiff(diff); r.setResultWave(resultWave);
        r.setTeamGlodenCount(teamGold); r.setTeamRedCount(teamRed);
        r.setBossId(bossId); r.setBossName(bossName); r.setBossDefeatFlag(bossDefeat);
        r.setGoldScale(g); r.setSilverScale(s); r.setBronzeScale(b);
        r.setWeapon1(CW[0]); r.setWeapon2(CW[1]); r.setWeapon3(CW[2]); r.setWeapon4(CW[3]);
        r.setWaveInfo(waveInfo); r.setJobScore(jobScore); r.setJobBonus(jobBonus); r.setSmellMeter(smell);
        r.setPlayedTime(LocalDateTime.now());
        return r;
    }

    static List<SplatoonCoopUserDetail> coopDetails() {
        List<SplatoonCoopUserDetail> all = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            all.addAll(coopDetailsFor(id));
        }
        return all;
    }

    static List<SplatoonCoopUserDetail> coopDetailsFor(long coopId) {
        List<SplatoonCoopUserDetail> ps = new ArrayList<>();
        String[] names = {"我", "Mako超长的名字测试", "りん", "Tao"};
        for (int i = 0; i < 4; i++) {
            SplatoonCoopUserDetail p = new SplatoonCoopUserDetail();
            p.setCoopId(String.valueOf(coopId));
            p.setMeFlag(i == 0);
            p.setPlayerName(names[i]);
            p.setDefeatEnemyCount(28 - i * 3);
            p.setDeliverGlodenCount(32 - i * 3);
            p.setAssistGlodenCount(i + 1);
            p.setDeliverRedCount(1143 - i * 80); // power 蛋上千,测宽度
            p.setRescueCount(i % 4); p.setRescuedCount((i + 1) % 3);
            ps.add(p);
        }
        return ps;
    }

    static List<SplatoonCoopWaveDetail> waves(long coopId) {
        List<SplatoonCoopWaveDetail> ws = new ArrayList<>();
        // 本地大招图按中文名命名,verify 里把 id 设成本地存在的名字以便图标解析
        ws.add(wave(coopId, 1, 1, null, 27, 41, "彈跳聲納"));
        ws.add(wave(coopId, 2, 2, "巨型海带", 30, 38, null));
        ws.add(wave(coopId, 3, 0, "雾", 25, 33, "鯊魚坐騎,螃蟹坦克"));
        return ws;
    }

    static SplatoonCoopWaveDetail wave(long coopId, int num, int water, String event, int norm, int deliver, String spIds) {
        SplatoonCoopWaveDetail w = new SplatoonCoopWaveDetail();
        w.setCoopId(String.valueOf(coopId)); w.setWaveNumber(num); w.setWaterLevel(water);
        w.setEventWaveName(event); w.setDeliverNorm(norm); w.setTeamDeliverCount(deliver); w.setSpecialWeaponIds(spIds);
        return w;
    }

    static List<SplatoonCoopEnemyDetail> enemies(long coopId) {
        List<SplatoonCoopEnemyDetail> es = new ArrayList<>();
        String[] names = {"鲑鱼", "蝙蝠", "铁壁", "高塔", "飞鱼", "蛇"};
        int[] cnt = {12, 8, 5, 3, 4, 2};
        for (int i = 0; i < names.length; i++) {
            SplatoonCoopEnemyDetail e = new SplatoonCoopEnemyDetail();
            e.setCoopId(String.valueOf(coopId)); e.setEnemyName(names[i]); e.setTeamDefeatCount(cnt[i]); e.setDefeatCount(cnt[i] / 2);
            es.add(e);
        }
        return es;
    }

    /* ---- util ---- */

    static void copy(File f, String name) throws Exception {
        Path dst = OUT.resolve(name);
        Files.copy(f.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("wrote " + dst + " (" + Files.size(dst) + " bytes)");
    }

    static void set(Object target, String field, Object val) throws Exception {
        Class<?> c = target instanceof Class ? (Class<?>) target : target.getClass();
        Field f = c.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target instanceof Class ? null : target, val);
    }

    static Object defaultReturn(Method m) {
        Class<?> rt = m.getReturnType();
        if (!rt.isPrimitive()) return null;
        if (rt == boolean.class) return false;
        if (rt == int.class || rt == long.class || rt == short.class || rt == byte.class) return 0;
        return null;
    }
}
