package com.bb.bot.handler.splatoon.render;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import java.time.Instant;
import java.time.ZoneId;
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

    @Autowired
    private SplatoonImageFetcher imageFetcher;

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    // 配色(与 demo 一致)
    private static final String BG = "#0d1020", CARD = "#1b2138", CARD2 = "#232a47", CHIP = "#2c3457";
    private static final String WIN = "#eaff3a", LOSE = "#aeb4c8", INK = "#e8ebf5", SUB = "#8b91a8";
    private static final String C_MY = "#39c6b4", C_EN = "#e85a8a", COOP = "#e07b00", GRADE = "#ffcf3f";
    private static final String SOFT = "#d6de72", GOLD = "#e2b95c", CYAN = "#6de1d2";

    /** 模式 id → {中文名, 左条颜色, 模式图标}；规则图标表、升降箭头表均收敛至 {@link SplatoonStyleConfig}。 */
    private static final Map<String, String[]> MODE = new HashMap<>();
    private static final Map<String, String> RULE_ICON = SplatoonStyleConfig.RULE_ICON;
    private static final Map<String, String> DIFF = SplatoonStyleConfig.POINT_DIFF;

    static {
        for (String modeId : SplatoonStyleConfig.MODE_STYLE.keySet()) {
            MODE.put(modeId, SplatoonStyleConfig.htmlMode(modeId));
        }
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
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='card' style='border-left:7px solid ").append(mode[1]).append(";'>");
        sb.append(battleCardHeader(r, mode, win));
        sb.append(battleCardTeams(ps, win));
        sb.append("</div>");
        return sb.toString();
    }

    /** 列表卡头部：模式/规则/地图名 + 地图缩略图 + 胜负/比分/meta 一行。 */
    private String battleCardHeader(SplatoonBattleRecord r, String[] mode, boolean win) {
        boolean ko = StringUtils.isNotBlank(r.getKnockout());
        String score = (r.getMyScore() != null || r.getOtherScore() != null)
                ? (n(r.getMyScore()) + " : " + n(r.getOtherScore())) : (win ? "胜利" : "败北");
        String meta = battleMeta(r);
        String jc = win ? WIN : LOSE;
        String koChip = ko ? " <span style='font-size:10px;background:" + WIN + ";color:#111;padding:0 5px;border-radius:5px;'>KO</span>" : "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='padding:9px 14px 7px;'><table><tr>");
        sb.append("<td style='vertical-align:middle;'>")
                .append("<span style='font-size:17px;'>").append(img(mode[2], 20, 20))
                .append(RULE_ICON.containsKey(r.getVsRuleId()) ? " " + img(RULE_ICON.get(r.getVsRuleId()), 19, 19) : "")
                .append(" ").append(esc(r.getVsStageName())).append("</span>")
                .append(" <span class='sub'>").append(esc(mode[0])).append(" · ").append(esc(r.getVsRuleName())).append("</span></td>");
        sb.append("<td style='width:140px;text-align:center;vertical-align:middle;'>")
                .append(img("nso_splatoon/battle/stage/" + r.getVsStageId() + ".png", 124, 23)).append("</td>");
        sb.append("<td style='text-align:right;vertical-align:middle;width:215px;white-space:nowrap;'>")
                .append("<span style='font-size:18px;color:").append(jc).append(";'>").append(win ? "WIN" : "LOSE").append(koChip).append("</span>")
                .append(" <span style='font-size:14px;color:").append(jc).append(";'>").append(esc(score)).append("</span>")
                .append(meta.isEmpty() ? "" : " <span class='sub'>" + esc(meta) + "</span>")
                .append("</td></tr></table></div>");
        return sb.toString();
    }

    /** 列表卡 4v4 两块（左己方右敌方），各 2×2。 */
    private String battleCardTeams(List<SplatoonBattleUserDetail> ps, boolean win) {
        return "<div style='padding:0 12px 9px;'><table><tr>"
                + "<td style='width:50%;vertical-align:top;padding-right:5px;'>" + teamBlock("#15323a", C_MY, "kill.png", "death.png", team(ps, 1), win) + "</td>"
                + "<td style='width:50%;vertical-align:top;padding-left:5px;'>" + teamBlock("#3a2030", C_EN, "kill2.png", "death2.png", team(ps, 2), !win) + "</td>"
                + "</tr></table></div>";
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

    /* ============================ SplatNet 扩展页面 ============================ */

    public File renderOverview(JSONObject home, JSONObject historySummary, JSONObject total) {
        JSONObject play = obj(obj(total, "data"), "playHistory");
        JSONObject historyPlay = obj(obj(historySummary, "data"), "playHistory");
        JSONObject currentPlayer = obj(obj(home, "data"), "currentPlayer");
        JSONObject weapon = obj(currentPlayer, "weapon");
        StringBuilder sb = new StringBuilder();
        sb.append(title("喷喷概览", "SplatNet 当前账号"));
        sb.append("<div class='card' style='padding:16px;'>")
                .append("<table><tr><td style='width:92px;text-align:center;'>").append(remoteImg(imgUrl(weapon), "weapon", 74, 74)).append("</td>")
                .append("<td><div style='font-size:23px;color:").append(INK).append(";'>").append(esc(or(currentPlayer.getString("name"), "当前玩家"))).append("</div>")
                .append("<div class='sub'>").append(esc(or(str(weapon, "name"), "SplatNet 3"))).append("</div></td>")
                .append("<td style='width:190px;text-align:right;'><div class='sub'>开始游玩</div><div style='font-size:16px;color:").append(SOFT).append(";'>")
                .append(esc(fmtTime(play.getString("gameStartTime")))).append("</div></td></tr></table></div>");
        sb.append(metricGrid(new String[][]{
                {"对战场数", fmtInt(play.get("battleNumTotal"))},
                {"累计涂地", fmtInt(play.get("paintPointTotal")) + "p"},
                {"最高段位", or(play.getString("udemaeMax"), "-")},
                {"活动比赛", fmtInt(num(obj(historyPlay, "leagueMatchPlayHistory"), "attend"))}
        }));
        sb.append("<div class='card' style='padding:14px 16px;'><div style='font-size:17px;color:").append(GOLD).append(";margin-bottom:8px;'>X 最高排名</div>")
                .append("<table><tr>").append(xModeCell(historyPlay, "区域", "xMatchMaxAr")).append(xModeCell(historyPlay, "塔楼", "xMatchMaxLf"))
                .append("</tr></table><table style='margin-top:7px;'><tr>").append(xModeCell(historyPlay, "鱼虎", "xMatchMaxGl")).append(xModeCell(historyPlay, "蛤蜊", "xMatchMaxCl")).append("</tr></table></div>");
        return render(sb.toString(), 760, 900);
    }

    public File renderCoopStatistics(JSONObject coopStatistics) {
        JSONObject data = obj(obj(coopStatistics, "data"), "coopRecord");
        StringBuilder sb = new StringBuilder();
        sb.append(title("打工统计", "舞台 / 头目 / 大型跑"));
        JSONArray stages = arr(data, "stageHighestRecords");
        sb.append("<div class='card' style='padding:14px 16px;'><div style='font-size:17px;color:").append(GOLD).append(";margin-bottom:8px;'>舞台最高评价</div>");
        for (int i = 0; i < Math.min(6, stages.size()); i++) {
            JSONObject item = stages.getJSONObject(i);
            JSONObject stage = obj(item, "coopStage");
            JSONObject grade = obj(item, "grade");
            sb.append(row(remoteImg(imgUrl(stage), "coop_stage", 46, 46), str(stage, "name"),
                    or(str(grade, "name"), "-") + " " + fmtInt(item.get("gradePoint")), GOLD));
        }
        sb.append("</div>");
        sb.append("<div class='card' style='padding:14px 16px;'><div style='font-size:17px;color:").append(GOLD).append(";margin-bottom:8px;'>头目鲑鱼击倒</div><table>");
        JSONArray bosses = arr(data, "defeatBossRecords");
        for (int i = 0; i < Math.min(6, bosses.size()); i++) {
            JSONObject item = bosses.getJSONObject(i);
            JSONObject enemy = obj(item, "enemy");
            sb.append("<tr><td style='width:44px;padding:5px;'>").append(remoteImg(imgUrl(enemy), "coop_enemy", 34, 34)).append("</td>")
                    .append("<td style='font-size:13px;'>").append(esc(str(enemy, "name"))).append("</td>")
                    .append("<td style='text-align:right;font-size:17px;color:").append(SOFT).append(";'>").append(fmtInt(item.get("defeatCount"))).append("</td></tr>");
        }
        sb.append("</table></div>");
        sb.append("<div class='card' style='padding:14px 16px;'><div style='font-size:17px;color:").append(GOLD).append(";margin-bottom:8px;'>大型跑 / 团队打工</div>");
        JSONArray bigRuns = arr(obj(obj(data, "bigRunRecord"), "records"), "edges");
        for (int i = 0; i < Math.min(3, bigRuns.size()); i++) {
            JSONObject node = obj(bigRuns.getJSONObject(i), "node");
            JSONObject stage = obj(node, "coopStage");
            sb.append(row(remoteImg(imgUrl(stage), "coop_stage", 52, 30), str(stage, "name"),
                    "最高 " + fmtInt(node.get("highestJobScore")) + " / " + fmtRankPercentile(node), SOFT));
        }
        JSONObject teamContest = obj(data, "teamContestRecord");
        sb.append("<div style='margin-top:8px;'>").append(chipRow(List.of(
                "金 " + fmtInt(teamContest.get("gold")),
                "银 " + fmtInt(teamContest.get("silver")),
                "铜 " + fmtInt(teamContest.get("bronze")),
                "参加 " + fmtInt(teamContest.get("attend"))))).append("</div></div>");
        return render(sb.toString(), 760, 1300);
    }

    public File renderXRankingHub(JSONObject xRanking) {
        JSONObject ranking = obj(obj(xRanking, "data"), "xRanking");
        JSONObject season = obj(ranking, "currentSeason");
        StringBuilder sb = new StringBuilder();
        sb.append(title("X 排名", or(str(season, "name"), "当前赛季") + " · ATLANTIC"));
        sb.append("<div class='card' style='padding:14px 16px;'>")
                .append("<div class='sub'>榜单更新时间</div><div style='font-size:17px;color:").append(SOFT).append(";'>")
                .append(esc(fmtTime(season.getString("lastUpdateTime")))).append("</div></div>");
        sb.append("<div class='card' style='padding:14px 16px;'><div style='font-size:17px;color:").append(GOLD).append(";margin-bottom:8px;'>各模式榜首</div>");
        sb.append(xTopOne(season, "区域", "xRankingAr"));
        sb.append(xTopOne(season, "塔楼", "xRankingLf"));
        sb.append(xTopOne(season, "鱼虎", "xRankingGl"));
        sb.append(xTopOne(season, "蛤蜊", "xRankingCl"));
        sb.append("</div>");
        return render(sb.toString(), 760, 820);
    }

    public File renderXRankingMode(String modeName, String region, JSONArray holders) {
        StringBuilder sb = new StringBuilder();
        sb.append(title("X 排名 · " + modeName, region + " 前十名"));
        sb.append("<div class='card' style='padding:10px 14px;'>");
        for (int i = 0; i < holders.size(); i++) {
            JSONObject node = holders.getJSONObject(i);
            JSONObject weapon = obj(node, "weapon");
            sb.append("<table style='background:").append(i < 3 ? "#242c4b" : CARD2).append(";border-radius:10px;margin-bottom:6px;'><tr>")
                    .append("<td style='width:54px;text-align:center;color:").append(i < 3 ? GOLD : SOFT).append(";font-size:19px;'>#").append(fmtInt(node.get("rank"))).append("</td>")
                    .append("<td style='width:52px;text-align:center;'>").append(remoteImg(imgUrl(weapon), "weapon", 40, 40)).append("</td>")
                    .append("<td><div style='font-size:15px;'>").append(esc(str(node, "name"))).append("</div><div class='sub'>")
                    .append(esc(str(node, "nameId"))).append(" · ").append(esc(str(weapon, "name"))).append("</div></td>")
                    .append("<td style='width:110px;text-align:right;padding-right:10px;color:").append(SOFT).append(";font-size:18px;'>")
                    .append(fmtOne(node.get("xPower"))).append("</td></tr></table>");
        }
        sb.append("</div>");
        return render(sb.toString(), 760, 1180);
    }

    public File renderEventBoard(JSONObject rankingPeriod) {
        JSONObject setting = obj(rankingPeriod, "leagueMatchSetting");
        JSONObject event = obj(setting, "leagueMatchEvent");
        JSONObject rule = obj(setting, "vsRule");
        JSONArray stages = arr(setting, "vsStages");
        StringBuilder sb = new StringBuilder();
        sb.append(title("活动比赛榜单", str(event, "name")));
        sb.append("<div class='card' style='padding:14px 16px;'>")
                .append("<div style='font-size:18px;color:").append(GOLD).append(";'>").append(esc(str(event, "name"))).append("</div>")
                .append("<div class='sub' style='margin-top:5px;'>").append(esc(str(event, "desc"))).append("</div>")
                .append("<div style='margin-top:8px;'>").append(chipRow(List.of(or(str(rule, "name"), "活动规则"), fmtTime(rankingPeriod.getString("startTime"))))).append("</div>")
                .append("<table style='margin-top:10px;'><tr>");
        for (int i = 0; i < stages.size(); i++) {
            JSONObject stage = stages.getJSONObject(i);
            sb.append("<td style='width:50%;padding-right:6px;'>").append(remoteImg(imgUrl(stage), "stage", 170, 55))
                    .append("<div class='sub'>").append(esc(str(stage, "name"))).append("</div></td>");
        }
        sb.append("</tr></table></div>");
        JSONArray teams = arr(rankingPeriod, "teams");
        for (int i = 0; i < teams.size(); i++) {
            JSONObject team = teams.getJSONObject(i);
            sb.append("<div class='card' style='padding:12px 14px;'><div style='font-size:16px;color:").append(GOLD).append(";margin-bottom:7px;'>")
                    .append(eventTeamName(team.getString("teamComposition"))).append("</div>");
            JSONArray details = arr(obj(team, "details"), "nodes");
            for (int j = 0; j < Math.min(10, details.size()); j++) {
                JSONObject node = details.getJSONObject(j);
                sb.append("<table style='background:").append(CARD2).append(";border-radius:9px;margin-bottom:5px;'><tr>")
                        .append("<td style='width:50px;text-align:center;color:").append(j < 3 ? GOLD : SOFT).append(";'>#").append(fmtInt(node.get("rank"))).append("</td>")
                        .append("<td style='width:78px;color:").append(SOFT).append(";font-size:15px;'>").append(fmtOne(node.get("power"))).append("</td>")
                        .append("<td>").append(eventPlayers(arr(node, "players"))).append("</td></tr></table>");
            }
            sb.append("</div>");
        }
        return render(sb.toString(), 820, 2200);
    }

    public File renderStageGear(JSONObject stageRecords, JSONObject equipments) {
        JSONObject data = obj(stageRecords, "data");
        JSONObject equipData = obj(equipments, "data");
        StringBuilder sb = new StringBuilder();
        sb.append(title("场地与装备", "舞台胜率 / 武器收藏"));
        JSONArray stages = arr(obj(data, "stageRecords"), "nodes");
        sb.append("<div class='card' style='padding:14px 16px;'><div style='font-size:17px;color:").append(GOLD).append(";margin-bottom:8px;'>场地记录</div>");
        for (int i = 0; i < Math.min(8, stages.size()); i++) {
            JSONObject stage = stages.getJSONObject(i);
            JSONObject stats = obj(stage, "stats");
            sb.append("<table style='background:").append(CARD2).append(";border-radius:10px;margin-bottom:6px;'><tr>")
                    .append("<td style='width:120px;text-align:center;'>").append(remoteImg(imgUrl(stage), "stage", 104, 35)).append("</td>")
                    .append("<td><div style='font-size:14px;'>").append(esc(str(stage, "name"))).append("</div><div class='sub'>最后游玩 ")
                    .append(esc(fmtTime(stats.getString("lastPlayedTime")))).append("</div></td>")
                    .append("<td style='width:190px;text-align:right;padding-right:10px;'>")
                    .append("<span style='color:").append(SOFT).append(";'>区域 ").append(fmtPct(stats.get("winRateAr"))).append("</span><br/>")
                    .append("<span class='sub'>塔楼 ").append(fmtPct(stats.get("winRateLf"))).append(" 鱼虎 ").append(fmtPct(stats.get("winRateGl"))).append(" 蛤蜊 ").append(fmtPct(stats.get("winRateCl"))).append("</span></td></tr></table>");
        }
        sb.append("</div>");
        JSONArray weapons = arr(obj(equipData, "weapons"), "nodes");
        weapons.sort((a, b) -> Integer.compare(num(obj((JSONObject) b, "stats"), "paint"), num(obj((JSONObject) a, "stats"), "paint")));
        sb.append("<div class='card' style='padding:14px 16px;'><div style='font-size:17px;color:").append(GOLD).append(";margin-bottom:8px;'>常用武器</div>");
        for (int i = 0; i < Math.min(8, weapons.size()); i++) {
            JSONObject weapon = weapons.getJSONObject(i);
            JSONObject stats = obj(weapon, "stats");
            sb.append(row(remoteImg(imgUrl(weapon), "weapon", 42, 42), str(weapon, "name"), fmtInt(stats.get("paint")) + "p", SOFT));
        }
        sb.append("</div>");
        sb.append(metricGrid(new String[][]{
                {"武器", String.valueOf(weapons.size())},
                {"头饰", String.valueOf(arr(obj(equipData, "headGears"), "nodes").size())},
                {"衣服", String.valueOf(arr(obj(equipData, "clothingGears"), "nodes").size())},
                {"鞋子", String.valueOf(arr(obj(equipData, "shoesGears"), "nodes").size())}
        }));
        return render(sb.toString(), 820, 1700);
    }

    public File renderFriends(JSONObject friends) {
        JSONArray nodes = arr(obj(obj(friends, "data"), "friends"), "nodes");
        int active = 0;
        for (int i = 0; i < nodes.size(); i++) {
            String state = nodes.getJSONObject(i).getString("onlineState");
            if (!"OFFLINE".equals(state)) {
                active++;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(title("喷喷好友", "在线/游玩中 " + active + " / " + nodes.size()));
        sb.append("<div class='card' style='padding:10px 12px;'>");
        for (int i = 0; i < Math.min(22, nodes.size()); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String state = onlineState(node.getString("onlineState"));
            String accent = state.contains("中") ? SOFT : ("在线".equals(state) ? CYAN : SUB);
            String mode = or(str(obj(node, "vsMode"), "name"), or(node.getString("coopRule"), ""));
            sb.append("<table style='background:").append(CARD2).append(";border-radius:10px;margin-bottom:6px;'><tr>")
                    .append("<td style='width:48px;text-align:center;'>").append(remoteImg(imgUrl(node.getJSONObject("userIcon")), "friend", 36, 36)).append("</td>")
                    .append("<td><div style='font-size:14px;'>").append(esc(or(node.getString("nickname"), node.getString("playerName")))).append("</div>")
                    .append("<div class='sub'>").append(esc(mode)).append("</div></td>")
                    .append("<td style='width:86px;text-align:right;padding-right:10px;color:").append(accent).append(";'>").append(esc(state)).append("</td></tr></table>");
        }
        sb.append("</div>");
        return render(sb.toString(), 560, 1500);
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

    private JSONObject obj(JSONObject parent, String key) {
        if (parent == null) {
            return new JSONObject();
        }
        JSONObject object = parent.getJSONObject(key);
        return object == null ? new JSONObject() : object;
    }

    private JSONArray arr(JSONObject parent, String key) {
        if (parent == null) {
            return new JSONArray();
        }
        JSONArray array = parent.getJSONArray(key);
        return array == null ? new JSONArray() : array;
    }

    private String str(JSONObject parent, String key) {
        return parent == null ? "" : or(parent.getString(key), "");
    }

    private String or(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private int num(JSONObject parent, String key) {
        if (parent == null) {
            return 0;
        }
        Number value = parent.getObject(key, Number.class);
        return value == null ? 0 : value.intValue();
    }

    private String imgUrl(JSONObject node) {
        if (node == null) {
            return "";
        }
        if (StringUtils.isNotBlank(node.getString("url"))) {
            return node.getString("url");
        }
        JSONObject image = node.getJSONObject("image");
        return image == null ? "" : or(image.getString("url"), "");
    }

    private String remoteImg(String url, String type, int w, int h) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        try {
            File file = imageFetcher.getImageFile(url, type);
            if (file != null && file.exists()) {
                return "<img src='" + file.toURI() + "' style='width:" + w + "px;height:" + h + "px;vertical-align:middle;'/>";
            }
        } catch (Exception e) {
            log.debug("splatoon remote image cache failed: {}", url, e);
        }
        return "<img src='" + esc(url) + "' style='width:" + w + "px;height:" + h + "px;vertical-align:middle;'/>";
    }

    private String fmtInt(Object value) {
        if (value == null) {
            return "0";
        }
        try {
            long n = value instanceof Number ? ((Number) value).longValue() : Long.parseLong(String.valueOf(value));
            return String.format("%,d", n);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String fmtOne(Object value) {
        if (value == null) {
            return "-";
        }
        try {
            double n = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
            return String.format("%.1f", n);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String fmtPct(Object value) {
        if (value == null) {
            return "-";
        }
        try {
            double n = value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(String.valueOf(value));
            if (n > 0 && n <= 1) {
                n *= 100;
            }
            return String.format("%.0f%%", n);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String fmtTime(String iso) {
        if (StringUtils.isBlank(iso)) {
            return "-";
        }
        try {
            return Instant.parse(iso).atZone(ZONE).format(TF);
        } catch (Exception e) {
            return iso;
        }
    }

    private String fmtRankPercentile(JSONObject node) {
        Object rank = node == null ? null : node.get("rankPercentile");
        if (rank == null) {
            rank = node == null ? null : node.get("trophy");
        }
        return rank == null ? "-" : String.valueOf(rank);
    }

    private String metricGrid(String[][] metrics) {
        StringBuilder sb = new StringBuilder("<div class='card' style='padding:14px 12px;'><table><tr>");
        for (String[] metric : metrics) {
            sb.append("<td style='width:25%;text-align:center;padding:6px 4px;'><div class='sub'>").append(esc(metric[0]))
                    .append("</div><div style='font-size:19px;color:").append(SOFT).append(";'>").append(esc(metric[1])).append("</div></td>");
        }
        return sb.append("</tr></table></div>").toString();
    }

    private String row(String image, String name, String value, String color) {
        return "<table style='background:" + CARD2 + ";border-radius:10px;margin-bottom:6px;'><tr>"
                + "<td style='width:64px;text-align:center;padding:5px;'>" + image + "</td>"
                + "<td><div style='font-size:14px;'>" + esc(name) + "</div></td>"
                + "<td style='width:170px;text-align:right;padding-right:10px;color:" + color + ";font-size:15px;'>" + esc(value) + "</td>"
                + "</tr></table>";
    }

    private String xRankCell(JSONObject play, String label, String key) {
        JSONObject rank = obj(play, key);
        return "<tr><td style='width:50%;background:" + CARD2 + ";border-radius:10px;padding:9px 12px;'>"
                + "<div class='sub'>" + label + "</div><div style='font-size:18px;color:" + SOFT + ";'>#" + esc(or(rank.getString("rank"), "-")) + "</div></td>"
                + "<td style='width:50%;background:" + CARD2 + ";border-radius:10px;padding:9px 12px;'>"
                + "<div class='sub'>X Power</div><div style='font-size:18px;color:" + SOFT + ";'>" + fmtOne(rank.get("xPower")) + "</div></td></tr>";
    }

    private String xModeCell(JSONObject play, String label, String key) {
        JSONObject rank = obj(play, key);
        String rankText = rank.get("rank") == null ? "-" : "#" + fmtInt(rank.get("rank"));
        String powerText = rank.get("power") == null ? "-" : fmtOne(rank.get("power"));
        String season = str(rank, "rankUpdateSeasonName");
        return "<td style='width:50%;background:" + CARD2 + ";border-radius:10px;padding:10px 12px;'>"
                + "<div class='sub'>" + esc(label) + "</div>"
                + "<div style='font-size:18px;color:" + SOFT + ";'>" + esc(rankText) + "</div>"
                + "<div class='sub'>XP " + esc(powerText) + (StringUtils.isBlank(season) ? "" : " · " + esc(season)) + "</div>"
                + "</td>";
    }

    private String xTopOne(JSONObject ranking, String modeName, String key) {
        JSONArray nodes = arr(obj(ranking, key), "nodes");
        JSONObject node = nodes.isEmpty() ? new JSONObject() : nodes.getJSONObject(0);
        JSONObject weapon = obj(node, "weapon");
        return "<table style='background:" + CARD2 + ";border-radius:10px;margin-bottom:7px;'><tr>"
                + "<td style='width:54px;text-align:center;color:" + GOLD + ";font-size:16px;'>" + esc(modeName) + "</td>"
                + "<td style='width:50px;text-align:center;'>" + remoteImg(imgUrl(weapon), "weapon", 38, 38) + "</td>"
                + "<td><div style='font-size:14px;'>" + esc(str(node, "name")) + "</div><div class='sub'>" + esc(str(weapon, "name")) + "</div></td>"
                + "<td style='width:105px;text-align:right;padding-right:10px;color:" + SOFT + ";font-size:17px;'>" + fmtOne(node.get("xPower")) + "</td></tr></table>";
    }

    private String eventTeamName(String composition) {
        if ("SOLO".equals(composition)) {
            return "单人榜";
        }
        if ("PAIR".equals(composition)) {
            return "双人榜";
        }
        if ("TEAM".equals(composition)) {
            return "四人榜";
        }
        return or(composition, "榜单");
    }

    private String eventPlayers(JSONArray players) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            JSONObject player = players.getJSONObject(i);
            JSONObject weapon = obj(player, "weapon");
            sb.append("<span style='white-space:nowrap;margin-right:8px;'>")
                    .append(remoteImg(imgUrl(weapon), "weapon", 24, 24))
                    .append(" ").append(esc(str(player, "name"))).append("</span>");
        }
        return sb.toString();
    }

    private String onlineState(String state) {
        if ("VS_MODE_FIGHTING".equals(state)) {
            return "比赛中";
        }
        if ("VS_MODE_MATCHING".equals(state)) {
            return "比赛匹配中";
        }
        if ("COOP_MODE_FIGHTING".equals(state)) {
            return "打工中";
        }
        if ("COOP_MODE_MATCHING".equals(state)) {
            return "打工匹配中";
        }
        if ("ONLINE".equals(state)) {
            return "在线";
        }
        if ("OFFLINE".equals(state)) {
            return "离线";
        }
        return or(state, "未知");
    }
}
