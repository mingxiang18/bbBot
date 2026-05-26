package com.bb.bot.handler.splatoon.render;

import com.bb.bot.common.util.FileUtils;
import com.bb.bot.common.util.ResourcesUtils;
import com.bb.bot.database.splatoon.entity.SplatoonBattleRecord;
import com.bb.bot.database.splatoon.entity.SplatoonBattleUserDetail;
import com.bb.bot.database.splatoon.entity.SplatoonCoopEnemyDetail;
import com.bb.bot.database.splatoon.entity.SplatoonCoopRecord;
import com.bb.bot.database.splatoon.entity.SplatoonCoopUserDetail;
import com.bb.bot.database.splatoon.entity.SplatoonCoopWaveDetail;
import com.openhtmltopdf.java2d.api.BufferedImagePageProcessor;
import com.openhtmltopdf.java2d.api.Java2DRendererBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 喷喷战绩 HTML→图 渲染器（openhtmltopdf,纯 Java,无浏览器）。视觉与定稿 demo 一致:
 * 对战/打工 的 列表(一屏多场) + 详情(单条全量)。资源读 static/nso_splatoon,字体 sakura,zh-CN。
 *
 * <p>缺图/缺字段一律优雅降级(略过),保证不出现错位重叠。一排多图标用表格格子排,规避
 * openhtmltopdf 不认内联图片 white-space:nowrap 的换行问题。</p>
 */
@Slf4j
@Component
public class SplatoonHtmlRenderer {

    @Autowired
    private ResourcesUtils resourcesUtils;

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 配色(与 demo 一致)
    private static final String BG = "#0d1020", CARD = "#1b2138", CARD2 = "#232a47", CHIP = "#2c3457";
    private static final String WIN = "#eaff3a", LOSE = "#aeb4c8", INK = "#e8ebf5", SUB = "#8b91a8";
    private static final String C_MY = "#39c6b4", C_EN = "#e85a8a", COOP = "#e07b00", GRADE = "#ffcf3f";

    /** 模式 id → {中文名, 左条颜色, 模式图标}。挑战/开放已按不同 id 区分。 */
    private static final Map<String, String[]> MODE = new HashMap<>();
    /** 规则 id → 规则图标路径。 */
    private static final Map<String, String> RULE_ICON = new HashMap<>();
    private static final Map<String, String> DIFF = new HashMap<>();

    static {
        MODE.put("VnNNb2RlLTE=", new String[]{"占地比赛", "#5fff1a", "nso_splatoon/battle/mode/regular.png"});
        MODE.put("VnNNb2RlLTUx", new String[]{"蛮颓比赛(开放)", "#ff3c1a", "nso_splatoon/battle/mode/rank.png"});
        MODE.put("VnNNb2RlLTI=", new String[]{"蛮颓比赛(挑战)", "#ff3c1a", "nso_splatoon/battle/mode/rank.png"});
        MODE.put("VnNNb2RlLTQ=", new String[]{"活动比赛", "#ff0062", "nso_splatoon/battle/mode/event.png"});
        MODE.put("VnNNb2RlLTU=", new String[]{"私人比赛", "#9500ff", "nso_splatoon/battle/mode/private.png"});
        MODE.put("VnNNb2RlLTM=", new String[]{"X比赛", "#008362", "nso_splatoon/battle/mode/x.png"});
        MODE.put("VnNNb2RlLTY=", new String[]{"祭典比赛", "#22dcff", "nso_splatoon/battle/mode/fest.png"});
        MODE.put("VnNNb2RlLTg=", new String[]{"三色夺宝比赛", "#22fff8", "nso_splatoon/battle/mode/fest.png"});
        RULE_ICON.put("VnNSdWxlLTI=", "nso_splatoon/battle/rule/ta.png");
        RULE_ICON.put("VnNSdWxlLTE=", "nso_splatoon/battle/rule/quyu.png");
        RULE_ICON.put("VnNSdWxlLTM=", "nso_splatoon/battle/rule/yuhu.png");
        RULE_ICON.put("VnNSdWxlLTQ=", "nso_splatoon/battle/rule/geli.png");
        DIFF.put("UP", "↑");
        DIFF.put("DOWN", "↓");
        DIFF.put("KEEP", "→");
    }

    /* ============================ 渲染管线 ============================ */

    @SneakyThrows
    private File render(String body, int wPx, int hPx) {
        String html = page(body, wPx, hPx);
        Java2DRendererBuilder b = new Java2DRendererBuilder();
        b.useFastMode();
        File font = safeFile("font/sakura.ttf");
        if (font != null) {
            b.useFont(font, "spl");
        }
        b.withHtmlContent(html, fileBase());
        // 3x 渲染 → 图标/文字更清晰(2x 偏糊)
        BufferedImagePageProcessor proc = new BufferedImagePageProcessor(BufferedImage.TYPE_INT_RGB, 3.0);
        b.toSinglePage(proc);
        b.runFirstPage();
        BufferedImage img = trimBottom(proc.getPageImages().get(0), 0x0d1020);
        File out = FileUtils.buildTmpFile();
        try (FileOutputStream os = new FileOutputStream(out)) {
            ImageIO.write(img, "png", os);
        }
        return out;
    }

    private String fileBase() {
        try {
            return resourcesUtils.getStaticResource("").toURI().toString();
        } catch (Exception e) {
            return "file:///";
        }
    }

    private BufferedImage trimBottom(BufferedImage src, int bgRgb) {
        int w = src.getWidth(), h = src.getHeight(), last = h - 1;
        outer:
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                if ((src.getRGB(x, y) & 0xFFFFFF) != bgRgb) { last = y; break outer; }
            }
        }
        return src.getSubimage(0, 0, w, Math.min(h, last + 24));
    }

    private String page(String body, int wPx, int hPx) {
        return "<html><head><style>"
                + "@page{size:" + wPx + "px " + hPx + "px;margin:0;}"
                + "*{margin:0;padding:0;}"
                + "html{background:" + BG + ";}"
                + "body{font-family:'spl';background:" + BG + ";color:" + INK + ";padding:10px;}"
                + ".card{background:" + CARD + ";border-radius:14px;margin-bottom:8px;overflow:hidden;}"
                + "table{border-collapse:collapse;width:100%;}"
                + ".sub{color:" + SUB + ";font-size:11px;}"
                + "</style></head><body>" + body + "</body></html>";
    }

    /* ============================ 资源 ============================ */

    private File safeFile(String subPath) {
        try {
            File f = resourcesUtils.getStaticResource(subPath);
            return f != null && f.exists() ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** <img>;文件缺失返回空串(优雅降级)。 */
    private String img(String subPath, int w, int h) {
        File f = safeFile(subPath);
        if (f == null) {
            return "";
        }
        return "<img src='" + f.toURI() + "' style='width:" + w + "px;height:" + h + "px;vertical-align:middle;'/>";
    }

    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String n(Object o) {
        return o == null ? "0" : String.valueOf(o);
    }

    /* ============================ 对战列表 ============================ */

    public File renderBattleList(List<SplatoonBattleRecord> records, List<SplatoonBattleUserDetail> details) {
        Map<String, List<SplatoonBattleUserDetail>> byBattle = details.stream()
                .collect(Collectors.groupingBy(SplatoonBattleUserDetail::getBattleId));
        StringBuilder sb = new StringBuilder();
        sb.append(title("对战记录", "最近 " + records.size() + " 场"));
        for (SplatoonBattleRecord r : records) {
            List<SplatoonBattleUserDetail> ps = byBattle.getOrDefault(String.valueOf(r.getId()), new ArrayList<>());
            sb.append(battleCard(r, ps, false));
        }
        return render(sb.toString(), 820, 200 + records.size() * 260);
    }

    public File renderBattleDetail(SplatoonBattleRecord r, List<SplatoonBattleUserDetail> details) {
        StringBuilder sb = new StringBuilder();
        sb.append(title("对战详情", "#" + r.getId()));
        sb.append(battleDetailCard(r, details));
        return render(sb.toString(), 760, 1600);
    }

    private List<SplatoonBattleUserDetail> team(List<SplatoonBattleUserDetail> ps, int flag) {
        List<SplatoonBattleUserDetail> t = ps.stream().filter(p -> p.getTeamFlag() != null && p.getTeamFlag() == flag)
                .sorted(Comparator.comparingInt(p -> (p.getMeFlag() != null && p.getMeFlag() == 1) ? 0 : 1))
                .collect(Collectors.toList());
        return t;
    }

    private String battleCard(SplatoonBattleRecord r, List<SplatoonBattleUserDetail> ps, boolean detail) {
        String[] mode = MODE.getOrDefault(r.getVsModeId(), new String[]{r.getVsModeName(), "#888", ""});
        boolean win = "WIN".equals(r.getJudgement());
        boolean ko = StringUtils.isNotBlank(r.getKnockout());
        String score = (r.getMyScore() != null || r.getOtherScore() != null)
                ? (n(r.getMyScore()) + " : " + n(r.getOtherScore())) : (win ? "胜利" : "败北");
        String meta = battleMeta(r);
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='card' style='border-left:7px solid ").append(mode[1]).append(";'>");
        // 头部
        sb.append("<div style='padding:9px 14px 7px;'><table><tr>");
        sb.append("<td style='vertical-align:middle;'>")
                .append("<span style='font-size:17px;'>").append(img(mode[2], 20, 20))
                .append(RULE_ICON.containsKey(r.getVsRuleId()) ? " " + img(RULE_ICON.get(r.getVsRuleId()), 19, 19) : "")
                .append(" ").append(esc(r.getVsStageName())).append("</span>")
                .append(" <span class='sub'>").append(esc(mode[0])).append(" · ").append(esc(r.getVsRuleName())).append("</span></td>");
        sb.append("<td style='width:140px;text-align:center;vertical-align:middle;'>")
                .append(img("nso_splatoon/battle/stage/" + r.getVsStageId() + ".png", 124, 23)).append("</td>");
        String jc = win ? WIN : LOSE;
        String koChip = ko ? " <span style='font-size:10px;background:" + WIN + ";color:#111;padding:0 5px;border-radius:5px;'>KO</span>" : "";
        sb.append("<td style='text-align:right;vertical-align:middle;width:215px;white-space:nowrap;'>")
                .append("<span style='font-size:18px;color:").append(jc).append(";'>").append(win ? "WIN" : "LOSE").append(koChip).append("</span>")
                .append(" <span style='font-size:14px;color:").append(jc).append(";'>").append(esc(score)).append("</span>")
                .append(meta.isEmpty() ? "" : " <span class='sub'>" + esc(meta) + "</span>")
                .append("</td></tr></table></div>");
        // 4v4 两块
        sb.append("<div style='padding:0 12px 9px;'><table><tr>")
                .append("<td style='width:50%;vertical-align:top;padding-right:5px;'>").append(teamBlock("#15323a", C_MY, "kill.png", "death.png", team(ps, 1), win)).append("</td>")
                .append("<td style='width:50%;vertical-align:top;padding-left:5px;'>").append(teamBlock("#3a2030", C_EN, "kill2.png", "death2.png", team(ps, 2), !win)).append("</td>")
                .append("</tr></table></div></div>");
        return sb.toString();
    }

    private String battleMeta(SplatoonBattleRecord r) {
        List<String> parts = new ArrayList<>();
        if (r.getDuration() != null) {
            parts.add(r.getDuration() / 60 + "'" + String.format("%02d", r.getDuration() % 60) + "\"");
        }
        if (StringUtils.isNotBlank(r.getRankCode())) {
            parts.add(r.getRankCode());
        }
        if (r.getPointChange() != null) {
            parts.add((r.getPointChange() > 0 ? "+" : "") + r.getPointChange() + "p");
        }
        if (r.getPower() != null) {
            parts.add("XP" + r.getPower());
        }
        return String.join(" · ", parts);
    }

    /** 4 人 2×2 块,队色底 + 队色击杀/阵亡图标。 */
    private String teamBlock(String tint, String color, String killIcon, String deathIcon, List<SplatoonBattleUserDetail> players, boolean win) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='background:").append(tint).append(";border-left:4px solid ").append(color).append(";border-radius:10px;padding:6px 8px;'><table>");
        for (int row = 0; row < 2; row++) {
            sb.append("<tr>");
            for (int c = 0; c < 2; c++) {
                int idx = row * 2 + c;
                sb.append("<td style='width:50%;'>");
                if (idx < players.size()) {
                    SplatoonBattleUserDetail p = players.get(idx);
                    String kc = win ? WIN : INK;
                    String ka = n(p.getKillCount()) + (p.getAssistCount() != null && p.getAssistCount() > 0 ? "(" + p.getAssistCount() + ")" : "");
                    sb.append("<table><tr>")
                            .append("<td style='width:32px;'>").append(img("nso_splatoon/weapon/" + p.getWeaponId() + ".png", 28, 28)).append("</td>")
                            .append("<td><div style='font-size:11px;'>").append(esc(p.getPlayerName())).append("</div>")
                            .append("<table style='margin-top:1px;'><tr>")
                            .append("<td style='width:40%;font-size:11px;white-space:nowrap;'>").append(img("nso_splatoon/battle/icon/" + killIcon, 19, 11)).append(" ").append(ka).append("</td>")
                            .append("<td style='width:28%;font-size:11px;white-space:nowrap;'>").append(img("nso_splatoon/battle/icon/" + deathIcon, 19, 11)).append(" ").append(n(p.getDeathCount())).append("</td>")
                            .append("<td style='width:32%;font-size:11px;white-space:nowrap;'>").append(img("nso_splatoon/specialWeapon/" + p.getWeaponSpecialId() + ".png", 15, 15)).append(" ").append(n(p.getSpecialCount())).append("</td>")
                            .append("</tr></table></td></tr></table>");
                }
                sb.append("</td>");
            }
            sb.append("</tr>");
        }
        return sb.append("</table></div>").toString();
    }

    private String battleDetailCard(SplatoonBattleRecord r, List<SplatoonBattleUserDetail> ps) {
        String[] mode = MODE.getOrDefault(r.getVsModeId(), new String[]{r.getVsModeName(), "#888", ""});
        boolean win = "WIN".equals(r.getJudgement());
        boolean ko = StringUtils.isNotBlank(r.getKnockout());
        String score = (r.getMyScore() != null || r.getOtherScore() != null) ? (n(r.getMyScore()) + " : " + n(r.getOtherScore())) : (win ? "胜利" : "败北");
        String jc = win ? WIN : LOSE;
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='card'>");
        // 头条
        sb.append("<div style='background:").append(mode[1]).append(";padding:14px 18px;'><table><tr>")
                .append("<td><div style='font-size:13px;color:#ffe;'>").append(esc(mode[0])).append(" · ").append(esc(r.getVsRuleName())).append("</div>")
                .append("<div style='font-size:28px;color:#fff;'>").append(RULE_ICON.containsKey(r.getVsRuleId()) ? img(RULE_ICON.get(r.getVsRuleId()), 26, 26) + " " : "").append(esc(r.getVsStageName())).append("</div></td>")
                .append("<td style='text-align:right;width:210px;'>")
                .append("<div style='font-size:30px;color:").append(jc).append(";line-height:1.1;'>").append(win ? "WIN" : "LOSE")
                .append(ko ? " <span style='font-size:12px;background:" + WIN + ";color:#111;padding:1px 7px;border-radius:6px;'>KO!</span>" : "").append("</div>")
                .append("<div style='font-size:18px;color:").append(jc).append(";'>").append(esc(score)).append("</div></td>")
                .append("</tr></table></div>");
        // 地图横幅
        String banner = bannerUri("nso_splatoon/battle/stage/" + r.getVsStageId() + ".png");
        if (banner != null) {
            sb.append("<div><img src='").append(banner).append("' style='width:100%;display:block;'/></div>");
        }
        // meta chips
        List<String> chips = new ArrayList<>();
        if (r.getDuration() != null) {
            chips.add("时长 " + r.getDuration() / 60 + "'" + String.format("%02d", r.getDuration() % 60) + "\"");
        }
        if (StringUtils.isNotBlank(r.getRankCode())) {
            chips.add("段位 " + r.getRankCode());
        }
        if (r.getPointChange() != null) {
            chips.add((r.getPointChange() > 0 ? "+" : "") + r.getPointChange() + "p");
        }
        if (r.getPower() != null) {
            chips.add("X-power " + r.getPower());
        }
        sb.append("<div style='padding:10px 18px 4px;'>").append(chipRow(chips)).append("</div>");
        // 奖牌
        if (StringUtils.isNotBlank(r.getAwards())) {
            sb.append("<div style='padding:2px 18px 8px;'>");
            for (String a : r.getAwards().split(",")) {
                sb.append("<span style='background:").append(GRADE).append(";color:#111;font-size:12px;padding:3px 10px;border-radius:10px;margin-right:8px;'>").append(esc(a)).append("</span>");
            }
            sb.append("</div>");
        }
        sb.append(detailTeam("己方队伍", C_MY, team(ps, 1), win));
        sb.append(detailTeam("敌方队伍", C_EN, team(ps, 2), !win));
        sb.append("</div>");
        return sb.toString();
    }

    private String detailTeam(String name, String color, List<SplatoonBattleUserDetail> players, boolean win) {
        StringBuilder sb = new StringBuilder("<div style='margin:0 14px 12px;'>");
        sb.append("<table style='margin-bottom:4px;'><tr><td style='border-left:6px solid ").append(color).append(";padding-left:8px;font-size:15px;color:").append(color).append(";'>").append(name).append("</td></tr></table>");
        for (SplatoonBattleUserDetail p : players) {
            String jc = win ? WIN : INK;
            String ka = n(p.getKillCount()) + (p.getAssistCount() != null && p.getAssistCount() > 0 ? "(" + p.getAssistCount() + ")" : "");
            String gear = StringUtils.isNotBlank(p.getGearPowers()) ? " / " + esc(p.getGearPowers()) : "";
            sb.append("<table style='background:").append(CARD2).append(";border-radius:10px;margin-bottom:5px;'><tr>")
                    .append("<td style='width:46px;padding:6px;'>").append(img("nso_splatoon/weapon/" + p.getWeaponId() + ".png", 40, 40)).append("</td>")
                    .append("<td><div style='font-size:13px;'>").append(esc(p.getPlayerName())).append("</div><div class='sub'>").append(esc(p.getWeaponName())).append(gear).append("</div></td>")
                    .append(col("涂地", n(p.getPaintCount()), INK)).append(col("K(A)", ka, jc)).append(col("D", n(p.getDeathCount()), INK))
                    .append("<td style='width:54px;text-align:center;'>").append(img("nso_splatoon/specialWeapon/" + p.getWeaponSpecialId() + ".png", 24, 24)).append(" ").append(n(p.getSpecialCount())).append("</td>")
                    .append("</tr></table>");
        }
        return sb.append("</div>").toString();
    }

    private String col(String label, String val, String color) {
        return "<td style='width:64px;text-align:center;'><span class='sub'>" + label + "</span><br/><span style='font-size:14px;color:" + color + ";'>" + val + "</span></td>";
    }

    /* ============================ 打工列表 ============================ */

    public File renderCoopList(List<SplatoonCoopRecord> records, List<SplatoonCoopUserDetail> details) {
        Map<String, List<SplatoonCoopUserDetail>> byCoop = details.stream()
                .collect(Collectors.groupingBy(SplatoonCoopUserDetail::getCoopId));
        StringBuilder sb = new StringBuilder();
        sb.append(title("打工记录", "最近 " + records.size() + " 场"));
        for (SplatoonCoopRecord r : records) {
            sb.append(coopCard(r, byCoop.getOrDefault(String.valueOf(r.getId()), new ArrayList<>())));
        }
        return render(sb.toString(), 820, 160 + records.size() * 230);
    }

    private String coopCard(SplatoonCoopRecord r, List<SplatoonCoopUserDetail> players) {
        boolean clear = r.getResultWave() != null && r.getResultWave() == 0;
        int totalWaves = r.getWaveInfo() != null ? r.getWaveInfo().split("·").length : 3;
        String waveText = clear ? ("W" + totalWaves) : ("W" + (r.getResultWave() == null ? "?" : r.getResultWave()));
        // 段位 + 评价点数(传说 130) + 升降箭头,一行显示
        String grade = "";
        if (StringUtils.isNotBlank(r.getAfterGradeName())) {
            grade = esc(r.getAfterGradeName())
                    + (r.getAfterGradePoint() != null ? " " + r.getAfterGradePoint() : "")
                    + (DIFF.containsKey(r.getGradePointDiff()) ? " " + DIFF.get(r.getGradePointDiff()) : "");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='card' style='border-left:7px solid ").append(COOP).append(";'>");
        // 头部
        sb.append("<div style='padding:6px 14px 2px;'><table><tr>");
        sb.append("<td style='vertical-align:middle;'><span style='font-size:18px;'>").append(esc(r.getCoopStageName())).append("</span>")
                .append(" <span class='sub'>").append(esc(coopRuleName(r.getRule()))).append(" · ").append(r.getPlayedTime() == null ? "" : r.getPlayedTime().format(TF)).append("</span></td>");
        sb.append("<td style='width:140px;text-align:center;vertical-align:middle;'>").append(img("nso_splatoon/coop/stage/" + r.getCoopStageName() + ".png", 124, 23)).append("</td>");
        sb.append("<td style='text-align:right;vertical-align:middle;width:230px;white-space:nowrap;'>")
                .append("<span style='font-size:17px;color:").append(clear ? WIN : LOSE).append(";'>").append(clear ? "Clear" : "Fail").append(" ").append(waveText).append("</span>")
                .append(grade.isEmpty() ? "" : " &#160;<span style='font-size:13px;color:" + GRADE + ";'>" + grade + "</span>")
                .append("</td></tr></table></div>");
        // 配枪 + 危险度 + Wave + boss + 金红蛋
        sb.append("<div style='padding:0 14px 5px;'><table><tr>");
        sb.append("<td style='width:150px;'><table style='width:auto;'><tr>");
        for (String w : new String[]{r.getWeapon1(), r.getWeapon2(), r.getWeapon3(), r.getWeapon4()}) {
            if (StringUtils.isNotBlank(w)) {
                sb.append("<td style='width:36px;text-align:center;'>").append(img("nso_splatoon/coop/weapon/" + w + ".png", 30, 30)).append("</td>");
            }
        }
        sb.append("</tr></table></td>");
        sb.append("<td style='font-size:11px;white-space:nowrap;'><span style='color:").append(GRADE).append(";'>危险度").append(esc(r.getDangerRate())).append("%</span>")
                .append(StringUtils.isNotBlank(r.getWaveInfo()) ? "&#160;&#160;<span class='sub'>" + esc(r.getWaveInfo()) + "</span>" : "").append("</td>");
        sb.append("<td style='text-align:right;white-space:nowrap;'>");
        if (StringUtils.isNotBlank(r.getBossName())) {
            sb.append(img("nso_splatoon/coop/boss/" + r.getBossId() + ".png", 30, 30)).append(" <span style='font-size:12px;color:").append(WIN).append(";'>").append(esc(r.getBossName()))
                    .append(Boolean.TRUE.equals(r.getBossDefeatFlag()) ? " ✓" : "").append("</span> ")
                    .append(img("nso_splatoon/coop/icon/gold_scale.png", 17, 17)).append(n(r.getGoldScale())).append(" ")
                    .append(img("nso_splatoon/coop/icon/sliver_scale.png", 17, 17)).append(n(r.getSilverScale())).append(" ")
                    .append(img("nso_splatoon/coop/icon/bronze_scale.png", 17, 17)).append(n(r.getBronzeScale())).append("&#160;&#160;&#160;");
        }
        sb.append(img("nso_splatoon/coop/icon/gold.png", 20, 20)).append(" ").append(n(r.getTeamGlodenCount())).append("&#160;&#160;")
                .append(img("nso_splatoon/coop/icon/red.png", 20, 20)).append(" ").append(n(r.getTeamRedCount()));
        sb.append("</td></tr></table></div>");
        // 队员
        sb.append("<div style='padding:2px 12px 8px;'><table><tr>");
        for (SplatoonCoopUserDetail p : players) {
            String pbg = Boolean.TRUE.equals(p.getMeFlag()) ? "#2c365e" : CARD2;
            String goldAssist = n(p.getDeliverGlodenCount()) + (p.getAssistGlodenCount() != null && p.getAssistGlodenCount() > 0 ? "<span class='sub'>(" + p.getAssistGlodenCount() + ")</span>" : "");
            sb.append("<td style='width:25%;vertical-align:top;padding:0 3px;'>")
                    .append("<div style='background:").append(pbg).append(";border-radius:8px;padding:5px 9px;'>")
                    // 名字独占一行(击倒移到下面金红蛋之后)
                    .append("<div style='font-size:12px;white-space:nowrap;overflow:hidden;'>").append(esc(p.getPlayerName())).append("</div>")
                    .append("<table style='margin-top:3px;'>")
                    .append("<tr><td style='width:50%;font-size:11px;white-space:nowrap;'>").append(img("nso_splatoon/coop/icon/gold.png", 17, 17)).append(" ").append(goldAssist).append("</td>")
                    .append("<td style='width:50%;font-size:11px;white-space:nowrap;'>").append(img("nso_splatoon/coop/icon/red.png", 17, 17)).append(" ").append(n(p.getDeliverRedCount())).append("</td></tr>")
                    // 金红蛋之后:击倒 | 救援/被救
                    .append("<tr><td style='font-size:11px;white-space:nowrap;'>").append(img("nso_splatoon/battle/icon/kill.png", 18, 11)).append(" ").append(n(p.getDefeatEnemyCount())).append("</td>")
                    .append("<td style='font-size:11px;white-space:nowrap;'>").append(img("nso_splatoon/coop/icon/rescue.png", 18, 13)).append(" ").append(n(p.getRescueCount())).append(" ").append(img("nso_splatoon/coop/icon/rescued.png", 18, 13)).append(" ").append(n(p.getRescuedCount())).append("</td></tr>")
                    .append("</table></div></td>");
        }
        sb.append("</tr></table></div></div>");
        return sb.toString();
    }

    /* ============================ 打工详情 ============================ */

    public File renderCoopDetail(SplatoonCoopRecord r, List<SplatoonCoopUserDetail> players,
                                 List<SplatoonCoopWaveDetail> waves, List<SplatoonCoopEnemyDetail> enemies) {
        boolean clear = r.getResultWave() != null && r.getResultWave() == 0;
        int totalWaves = waves != null ? waves.size() : 3;
        String waveText = clear ? ("W" + totalWaves) : ("W" + (r.getResultWave() == null ? "?" : r.getResultWave()));
        // 段位 + 评价点数(传说 130) + 升降箭头,一行显示
        String grade = "";
        if (StringUtils.isNotBlank(r.getAfterGradeName())) {
            grade = esc(r.getAfterGradeName())
                    + (r.getAfterGradePoint() != null ? " " + r.getAfterGradePoint() : "")
                    + (DIFF.containsKey(r.getGradePointDiff()) ? " " + DIFF.get(r.getGradePointDiff()) : "");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(title("打工详情", "#" + r.getId()));
        sb.append("<div class='card'>");
        // 头条
        sb.append("<div style='background:").append(COOP).append(";padding:14px 18px;'><table><tr>")
                .append("<td><div style='font-size:13px;color:#ffe;'>").append(esc(coopRuleName(r.getRule()))).append("</div><div style='font-size:26px;color:#fff;'>").append(esc(r.getCoopStageName())).append("</div></td>")
                .append("<td style='text-align:right;'><div style='font-size:24px;color:").append(clear ? WIN : LOSE).append(";'>").append(clear ? "Clear" : "Fail").append(" <span style='font-size:13px;color:#fff;'>").append(waveText).append("</span></div>")
                .append("<div style='font-size:14px;color:#fff;'>危险度 ").append(esc(r.getDangerRate())).append("% ").append(grade.isEmpty() ? "" : "· " + grade).append("</div></td>")
                .append("</tr></table></div>");
        // 地图横幅
        String banner = bannerUri("nso_splatoon/coop/stage/" + r.getCoopStageName() + ".png");
        if (banner != null) {
            sb.append("<div><img src='").append(banner).append("' style='width:100%;display:block;'/></div>");
        }
        // 统计条
        sb.append("<div style='padding:12px 18px 4px;'><table style='background:").append(CARD2).append(";border-radius:10px;'><tr>")
                .append(kv("得分", n(r.getJobScore()))).append(kv("熊点", r.getJobBonus() == null ? "-" : "+" + r.getJobBonus()))
                .append(kv("金蛋", n(r.getTeamGlodenCount()))).append(kv("红蛋", n(r.getTeamRedCount())))
                .append("</tr></table></div>");
        // 气味计
        if (r.getSmellMeter() != null) {
            sb.append("<div style='padding:4px 18px 8px;'><div class='sub'>头目鲑鱼气味计 ").append(r.getSmellMeter()).append("/5</div>").append(bar(r.getSmellMeter() * 20)).append("</div>");
        }
        // waves
        sb.append("<div style='padding:0 18px 0;'>");
        if (waves != null) {
            for (SplatoonCoopWaveDetail w : waves) {
                String ev = StringUtils.isNotBlank(w.getEventWaveName()) ? esc(w.getEventWaveName()) : waterLevelText(w.getWaterLevel());
                // 大招用图标(按 id),不再用文字
                StringBuilder sp = new StringBuilder();
                if (StringUtils.isNotBlank(w.getSpecialWeaponIds())) {
                    sp.append("<span class='sub'>大招</span> ");
                    for (String sid : w.getSpecialWeaponIds().split(",")) {
                        if (StringUtils.isNotBlank(sid)) {
                            sp.append(img("nso_splatoon/coop/specialWeapon/" + sid.trim() + ".png", 24, 24)).append("&#160;");
                        }
                    }
                }
                sb.append("<table style='background:").append(CARD2).append(";border-radius:10px;margin-bottom:5px;'><tr>")
                        .append("<td style='width:48px;text-align:center;font-size:15px;color:").append(WIN).append(";'>W").append(n(w.getWaveNumber())).append("</td>")
                        .append("<td style='width:120px;' class='sub'>").append(ev).append("</td>")
                        .append("<td><span class='sub'>达标</span> ").append(n(w.getDeliverNorm())).append("&#160;&#160;<span class='sub'>送蛋</span> ").append(n(w.getTeamDeliverCount())).append("</td>")
                        .append("<td style='text-align:right;padding-right:10px;white-space:nowrap;'>").append(sp).append("</td></tr></table>");
            }
        }
        // EX 头目鲑鱼(打了 boss 才显示)
        if (StringUtils.isNotBlank(r.getBossName())) {
            sb.append("<table style='background:#2c365e;border-radius:10px;margin-bottom:5px;'><tr>")
                    .append("<td style='width:64px;text-align:center;padding:4px;'>").append(img("nso_splatoon/coop/boss/" + r.getBossId() + ".png", 54, 54)).append("</td>")
                    .append("<td><div style='font-size:15px;color:").append(WIN).append(";'>EX · ").append(esc(r.getBossName())).append(" <span style='font-size:12px;color:#fff;'>").append(Boolean.TRUE.equals(r.getBossDefeatFlag()) ? "讨伐成功" : "讨伐失败").append("</span></div></td>")
                    .append("<td style='text-align:right;padding-right:10px;white-space:nowrap;'>")
                    .append(img("nso_splatoon/coop/icon/gold_scale.png", 22, 22)).append(" ").append(n(r.getGoldScale())).append("&#160;&#160;")
                    .append(img("nso_splatoon/coop/icon/sliver_scale.png", 22, 22)).append(" ").append(n(r.getSilverScale())).append("&#160;&#160;")
                    .append(img("nso_splatoon/coop/icon/bronze_scale.png", 22, 22)).append(" ").append(n(r.getBronzeScale())).append("</td></tr></table>");
        }
        sb.append("</div>");
        // 队员
        sb.append("<div style='padding:8px 18px 4px;'><div style='font-size:14px;color:").append(GRADE).append(";'>队员表现</div></div><div style='padding:0 14px 14px;'>");
        for (SplatoonCoopUserDetail p : players) {
            String pbg = Boolean.TRUE.equals(p.getMeFlag()) ? "#2c365e" : CARD2;
            String goldAssist = n(p.getDeliverGlodenCount()) + (p.getAssistGlodenCount() != null && p.getAssistGlodenCount() > 0 ? "(" + p.getAssistGlodenCount() + ")" : "");
            sb.append("<table style='background:").append(pbg).append(";border-radius:10px;margin-bottom:5px;'><tr>")
                    .append("<td style='width:90px;padding:6px 0 6px 12px;font-size:13px;'>").append(esc(p.getPlayerName())).append("</td>")
                    .append(iconCell("nso_splatoon/battle/icon/kill.png", 17, 10, "击倒", n(p.getDefeatEnemyCount())))
                    .append(iconCell("nso_splatoon/coop/icon/gold.png", 16, 16, "金蛋", goldAssist))
                    .append(iconCell("nso_splatoon/coop/icon/red.png", 16, 16, "红蛋", n(p.getDeliverRedCount())))
                    .append(iconCell("nso_splatoon/coop/icon/rescue.png", 17, 13, "救援", n(p.getRescueCount())))
                    .append(iconCell("nso_splatoon/coop/icon/rescued.png", 17, 13, "被救", n(p.getRescuedCount())))
                    .append("</tr></table>");
        }
        sb.append("</div>");
        // 敌人击倒
        if (enemies != null && !enemies.isEmpty()) {
            sb.append("<div style='padding:0 18px 16px;'><div style='font-size:13px;color:").append(GRADE).append(";margin-bottom:4px;'>敌人击倒</div><span class='sub'>");
            for (SplatoonCoopEnemyDetail e : enemies) {
                sb.append(esc(e.getEnemyName())).append(" ").append(n(e.getTeamDefeatCount())).append("&#160;&#160;");
            }
            sb.append("</span></div>");
        }
        sb.append("</div>");
        return render(sb.toString(), 760, 1800);
    }

    /* ============================ 小工具 ============================ */

    private String title(String t, String sub) {
        return "<table style='margin:2px 4px 8px;'><tr><td style='font-size:22px;'>" + t + "</td>"
                + "<td style='text-align:right;' class='sub'>" + esc(sub) + "</td></tr></table>";
    }

    private String chipRow(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String it : items) {
            if (StringUtils.isBlank(it)) {
                continue;
            }
            sb.append("<span style='background:").append(CHIP).append(";color:#c5c9da;font-size:11px;padding:2px 9px;border-radius:9px;margin-right:6px;'>").append(esc(it)).append("</span>");
        }
        return sb.toString();
    }

    private String kv(String k, String v) {
        return "<td style='text-align:center;padding:8px;'><div class='sub'>" + k + "</div><div style='font-size:16px;'>" + v + "</div></td>";
    }

    private String iconCell(String icon, int iw, int ih, String label, String val) {
        return "<td style='width:100px;text-align:center;'><div class='sub'>" + img(icon, iw, ih) + " " + label + "</div><div style='font-size:15px;'>" + val + "</div></td>";
    }

    private String bar(int pct) {
        pct = Math.max(0, Math.min(100, pct));
        return "<div style='background:#11152a;border-radius:6px;height:12px;margin-top:4px;'><div style='background:" + WIN + ";width:" + pct + "%;height:12px;border-radius:6px;'>&#160;</div></div>";
    }

    private String bannerUri(String subPath) {
        File f = safeFile(subPath);
        return f == null ? null : f.toURI().toString();
    }

    private String coopRuleName(String rule) {
        if (rule == null) {
            return "打工";
        }
        switch (rule) {
            case "REGULAR": return "普通打工";
            case "TEAM_CONTEST": return "团队打工";
            case "BIG_RUN": return "巨大跑 · BIG RUN";
            default: return rule;
        }
    }

    private String waterLevelText(Integer level) {
        if (level == null) {
            return "普通";
        }
        switch (level) {
            case 0: return "干潮";
            case 1: return "普通";
            case 2: return "满潮";
            default: return "普通";
        }
    }
}
