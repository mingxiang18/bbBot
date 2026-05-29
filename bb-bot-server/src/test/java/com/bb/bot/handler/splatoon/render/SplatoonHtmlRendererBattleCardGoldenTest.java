package com.bb.bot.handler.splatoon.render;

import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;
import com.bb.bot.database.splatoon.entity.SplatoonBattleUserDetail;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code SplatoonHtmlRenderer.battleCard} 的字符串级 golden 测试（T7.6）。
 *
 * <p>{@code battleCard} 是纯 HTML 字符串拼接：唯一的外部依赖是 {@code resourcesUtils}，
 * 而它只在 {@code safeFile} 里被调用且整段被 try/catch 包住——把 {@code resourcesUtils}
 * 置空后所有 {@code img(...)} 优雅降级为空串，于是给定相同的 record/details，输出是
 * 完全确定的字符串。本测试据此把重构（拆 {@code battleCardHeader}/{@code battleCardTeams}）
 * 前后的输出锁死为字节级相等（HTML 字符串相等口径，强于 D2 像素容差）。</p>
 *
 * <p>覆盖列表模式（{@code detail=false}）与详情模式（{@code detail=true}，对应原方法的
 * {@code detail} 形参分支），以及 WIN/LOSE、KO/非 KO、有比分/无比分、命中模式表/兜底默认值
 * 等分支，确保拆分后控制流等价。golden 文本随测试一并提交在
 * {@code src/test/resources/golden/} 下。</p>
 */
class SplatoonHtmlRendererBattleCardGoldenTest {

    private static final Path GOLDEN_DIR = Paths.get("src/test/resources/golden");

    private SplatoonHtmlRenderer newRenderer() {
        SplatoonHtmlRenderer r = new SplatoonHtmlRenderer();
        // resourcesUtils 留空：safeFile 走 catch 分支返回 null，img(...) 全部降级为空串，输出确定。
        ReflectionTestUtils.setField(r, "resourcesUtils", null);
        return r;
    }

    private String battleCard(SplatoonHtmlRenderer r, SplatoonBattleRecord rec,
                              List<SplatoonBattleUserDetail> ps, boolean detail) {
        return (String) ReflectionTestUtils.invokeMethod(r, "battleCard", rec, ps, detail);
    }

    /** 命中模式表 + WIN + KO + 数字比分 + 满 4v4，列表模式。 */
    private SplatoonBattleRecord winKoRecord() {
        SplatoonBattleRecord r = new SplatoonBattleRecord();
        r.setId(128L);
        r.setVsModeId("VnNNb2RlLTUx"); // 蛮颓比赛(开放)
        r.setVsModeName("蛮颓比赛(开放)");
        r.setVsRuleId("VnNSdWxlLTE=");
        r.setVsRuleName("区域控制");
        r.setVsStageId("stage-7");
        r.setVsStageName("真鲭跳台");
        r.setJudgement("WIN");
        r.setKnockout("KNOCKOUT_WIN");
        r.setMyScore("100");
        r.setOtherScore("0");
        r.setDuration(162);
        r.setRankCode("S+0");
        r.setPointChange(8);
        r.setPower(2483);
        return r;
    }

    /** 未命中模式表（走兜底 vsModeName/#888）+ LOSE + 无 KO + 无比分（走"败北"）+ 空队伍。 */
    private SplatoonBattleRecord loseFallbackRecord() {
        SplatoonBattleRecord r = new SplatoonBattleRecord();
        r.setId(7L);
        r.setVsModeId("UNKNOWN_MODE");
        r.setVsModeName("神秘模式");
        r.setVsRuleId("UNKNOWN_RULE");
        r.setVsRuleName("神秘规则");
        r.setVsStageId("stage-x");
        r.setVsStageName("神秘场地");
        r.setJudgement("LOSE");
        // 不设 score / knockout / meta
        return r;
    }

    private List<SplatoonBattleUserDetail> fourVsFour() {
        List<SplatoonBattleUserDetail> ps = new ArrayList<>();
        for (int team = 1; team <= 2; team++) {
            for (int i = 0; i < 4; i++) {
                SplatoonBattleUserDetail p = new SplatoonBattleUserDetail();
                p.setBattleId("128");
                p.setTeamFlag(team);
                p.setMeFlag(team == 1 && i == 0 ? 1 : 0);
                p.setPlayerName("P" + team + "_" + i);
                p.setWeaponId("w" + team + i);
                p.setWeaponSpecialId("sp" + team + i);
                p.setKillCount(10 - i);
                p.setAssistCount(i);
                p.setDeathCount(i + 1);
                p.setSpecialCount(i);
                p.setGearPowers("防御↑");
                ps.add(p);
            }
        }
        return ps;
    }

    @Test
    void battleCard_winKo_list_matchesGolden() throws Exception {
        String html = battleCard(newRenderer(), winKoRecord(), fourVsFour(), false);
        assertMatchesGolden("battle-card-winko-list.html", html);
    }

    @Test
    void battleCard_winKo_detailFlag_matchesGolden() throws Exception {
        String html = battleCard(newRenderer(), winKoRecord(), fourVsFour(), true);
        assertMatchesGolden("battle-card-winko-detailflag.html", html);
    }

    @Test
    void battleCard_loseFallback_emptyTeams_matchesGolden() throws Exception {
        String html = battleCard(newRenderer(), loseFallbackRecord(), new ArrayList<>(), false);
        assertMatchesGolden("battle-card-lose-fallback.html", html);
    }

    private void assertMatchesGolden(String fileName, String actual) throws Exception {
        String resourcePath = "golden/" + fileName;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                // 首次运行：写入参考文件（随提交固化），后续运行进入断言分支。
                Files.createDirectories(GOLDEN_DIR);
                Files.write(GOLDEN_DIR.resolve(fileName), actual.getBytes(StandardCharsets.UTF_8));
                throw new IllegalStateException("golden 不存在，已生成 " + GOLDEN_DIR.resolve(fileName)
                        + "，请确认后重跑（提交前应已固化）。");
            }
            String expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(actual).isEqualTo(expected);
        }
    }
}
