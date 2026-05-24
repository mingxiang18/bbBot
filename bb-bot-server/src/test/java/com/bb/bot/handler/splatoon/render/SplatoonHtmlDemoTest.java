package com.bb.bot.handler.splatoon.render;

import com.openhtmltopdf.java2d.api.BufferedImagePageProcessor;
import com.openhtmltopdf.java2d.api.Java2DRendererBuilder;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * 喷喷战绩 HTML->图 渲染观感 DEMO（openhtmltopdf，纯 Java，无浏览器）。
 * 输出到 target/splatoon-demo/*.png 供视觉审核，非正式功能。
 */
public class SplatoonHtmlDemoTest {

    static final String ASSET = "file:///tmp/spl-demo-assets/";
    static final File FONT = new File("/tmp/spl-demo-assets/sakura.ttf");
    static final File OUT = new File("target/splatoon-demo");

    static final String BG = "#0d1020";
    static final String CARD = "#1b2138";
    static final String CARD2 = "#232a47";
    static final String CHIP = "#2c3457";
    static final String WIN = "#eaff3a";
    static final String LOSE = "#aeb4c8";
    static final String INK = "#e8ebf5";
    static final String SUB = "#8b91a8";
    static final String C_MY = "#39c6b4";
    static final String C_EN = "#e85a8a";

    public static void main(String[] args) throws Exception {
        new SplatoonHtmlDemoTest().renderDemos();
    }

    @Test
    public void renderDemos() throws Exception {
        OUT.mkdirs();
        write("battle-list.png", battleListHtml(), 820, 1600);
        write("battle-detail.png", battleDetailHtml(), 760, 1400);
        write("coop-list.png", coopListHtml(), 820, 1400);
        write("coop-detail.png", coopDetailHtml(), 760, 1400);
        System.out.println("DEMO 输出: " + OUT.getAbsolutePath());
    }

    /* --------------------------- 渲染管线 --------------------------- */

    void write(String name, String body, int wPx, int hPx) throws Exception {
        String html = page(body, wPx, hPx);
        Java2DRendererBuilder b = new Java2DRendererBuilder();
        b.useFastMode();
        b.useFont(FONT, "spl");
        b.withHtmlContent(html, ASSET);
        BufferedImagePageProcessor proc = new BufferedImagePageProcessor(BufferedImage.TYPE_INT_RGB, 2.0);
        b.toSinglePage(proc);
        b.runFirstPage();
        List<BufferedImage> imgs = proc.getPageImages();
        BufferedImage img = trimBottom(imgs.get(0), 0x0d1020);
        try (FileOutputStream os = new FileOutputStream(new File(OUT, name))) {
            ImageIO.write(img, "png", os);
        }
        System.out.println("wrote " + name + " " + img.getWidth() + "x" + img.getHeight());
    }

    BufferedImage trimBottom(BufferedImage src, int bgRgb) {
        int w = src.getWidth(), h = src.getHeight(), last = h - 1;
        outer:
        for (int y = h - 1; y >= 0; y--)
            for (int x = 0; x < w; x++)
                if ((src.getRGB(x, y) & 0xFFFFFF) != bgRgb) { last = y; break outer; }
        return src.getSubimage(0, 0, w, Math.min(h, last + 24));
    }

    String page(String body, int wPx, int hPx) {
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

    /* --------------------------- 通用组件 --------------------------- */

    String title(String t, String sub) {
        return "<table style='margin:2px 4px 8px;'><tr>"
                + "<td style='font-size:22px;'>" + t + "</td>"
                + "<td style='text-align:right;' class='sub'>" + sub + "</td></tr></table>";
    }

    String img(String n, int w, int h) {
        return "<img src='" + ASSET + n + "' style='width:" + w + "px;height:" + h + "px;vertical-align:middle;'/>";
    }

    /** 干净的结果块：胜负大字 + KO 角标 + 比分一行。 */
    String resultBlock(boolean win, boolean ko, String score, int judgeSize) {
        String jc = win ? WIN : LOSE;
        String judge = win ? "WIN" : "LOSE";
        String koChip = ko ? " <span style='font-size:11px;background:" + WIN + ";color:#111;padding:1px 6px;border-radius:6px;'>KO</span>" : "";
        return "<div style='font-size:" + judgeSize + "px;color:" + jc + ";line-height:1.1;'>" + judge + koChip + "</div>"
                + "<div style='font-size:" + (judgeSize - 8) + "px;color:" + jc + ";'>" + score + "</div>";
    }

    /** 灰色小 chip 拼成的一行 meta（时长/段位/分变等）。 */
    String chips(String... items) {
        StringBuilder sb = new StringBuilder("<div style='margin-top:6px;'>");
        for (String it : items) {
            if (it == null || it.isEmpty()) continue;
            sb.append("<span style='background:").append(CHIP).append(";color:#c5c9da;font-size:11px;padding:2px 9px;border-radius:9px;margin-right:6px;'>").append(it).append("</span>");
        }
        return sb.append("</div>").toString();
    }

    /* --------------------------- 对战列表（含全队 4v4） --------------------------- */

    String battleListHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append(title("对战记录", "账号1 · 最近 5 场"));
        sb.append(battleListCard("#e2402a","mode.png","rule.png","真鲭跳台","蛮颓(开放)·区域","2'42\"",true,true,"100 : 0","S+0 +8p",
                new String[][]{{"w1.png","我","12(3)","4","3","sp1.png"},{"w2.png","あおい","8(1)","6","2","sp2.png"},{"w3.png","Koharu","6(4)","5","4","sp3.png"},{"w4.png","ナギ","10(2)","7","3","sp4.png"}},
                new String[][]{{"w5.png","Rio","5(2)","8","2","sp1.png"},{"w6.png","つばさ","7(1)","9","1","sp2.png"},{"w7.png","Leo","4(3)","10","3","sp3.png"},{"w8.png","ゆうき","6(0)","7","2","sp4.png"}}));
        sb.append(battleListCard("#48d932","mode.png","","鱼板拼盘","占地比赛","3'00\"",false,false,"38.2 : 61.8","",
                new String[][]{{"w2.png","我","6(2)","9","2","sp2.png"},{"w3.png","Yuki","5(1)","8","3","sp3.png"},{"w1.png","Sora","7(0)","6","1","sp1.png"},{"w4.png","Mei","4(2)","7","2","sp4.png"}},
                new String[][]{{"w5.png","Kai","9(1)","4","4","sp1.png"},{"w6.png","Ren","8(2)","5","2","sp2.png"},{"w7.png","Aoi","6(3)","6","3","sp3.png"},{"w8.png","Hina","7(1)","5","2","sp4.png"}}));
        sb.append(battleListCard("#16a085","mode.png","rule.png","金枪鱼美术馆","X比赛·真格鱼虎","4'05\"",true,false,"胜利","XP 2483",
                new String[][]{{"w3.png","我","9(5)","7","3","sp3.png"},{"w1.png","Aki","11(2)","5","2","sp1.png"},{"w2.png","Yo","7(3)","8","1","sp2.png"},{"w4.png","Ui","8(1)","6","2","sp4.png"}},
                new String[][]{{"w6.png","Ken","6(2)","9","2","sp2.png"},{"w5.png","Rei","8(0)","7","3","sp1.png"},{"w8.png","Sho","5(4)","8","1","sp4.png"},{"w7.png","Mao","7(1)","7","2","sp3.png"}}));
        sb.append(battleListCard("#e2402a","mode.png","rule.png","海女美术大学","蛮颓(挑战)·真格塔楼","3'48\"",true,false,"胜利","S+1 +12p",
                new String[][]{{"w4.png","我","14(2)","3","4","sp4.png"},{"w2.png","Hina","9(3)","5","2","sp2.png"},{"w1.png","Ren","7(1)","6","1","sp1.png"},{"w3.png","Kai","8(4)","5","3","sp3.png"}},
                new String[][]{{"w7.png","Leo","6(2)","9","2","sp3.png"},{"w8.png","Mei","7(0)","8","2","sp4.png"},{"w5.png","Sora","5(3)","10","1","sp1.png"},{"w6.png","Yuki","6(1)","9","2","sp2.png"}}));
        sb.append(battleListCard("#48d932","mode.png","","古鲸鱼工厂","占地比赛","3'00\"",false,false,"45.1 : 54.9","",
                new String[][]{{"w1.png","我","5(1)","8","2","sp1.png"},{"w2.png","Aoi","4(2)","9","1","sp2.png"},{"w3.png","Tao","6(0)","7","3","sp3.png"},{"w4.png","Rio","5(3)","8","2","sp4.png"}},
                new String[][]{{"w5.png","Ken","8(1)","5","3","sp1.png"},{"w6.png","Sho","9(2)","4","2","sp2.png"},{"w7.png","Ui","7(3)","6","1","sp3.png"},{"w8.png","Yo","8(0)","5","2","sp4.png"}}));
        return sb.toString();
    }

    /** 紧凑对战列表卡片:左半区=队伍1(2×2),右半区=队伍2(2×2),各带队色底,含地图缩略图。一屏 5+ 场。 */
    String battleListCard(String accent, String modeImg, String ruleImg, String stage, String modeName, String time,
                          boolean win, boolean ko, String score, String meta,
                          String[][] team1, String[][] team2) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='card' style='border-left:7px solid ").append(accent).append(";'>");
        // 头部一行:模式/规则/地图名/时间 | 地图缩略图(居中) | 胜负/比分/meta
        sb.append("<div style='padding:9px 14px 7px;'><table><tr>");
        sb.append("<td style='vertical-align:middle;'>")
                .append("<span style='font-size:17px;'>").append(img(modeImg, 20, 20))
                .append(ruleImg.isEmpty() ? "" : " " + img(ruleImg, 19, 19)).append(" ").append(stage).append("</span>")
                .append(" <span class='sub'>").append(modeName).append(" · ").append(time).append("</span>")
                .append("</td>");
        sb.append("<td style='width:140px;text-align:center;vertical-align:middle;'>").append(img("stage.png", 124, 23)).append("</td>");
        String jc = win ? WIN : LOSE;
        String koChip = ko ? " <span style='font-size:10px;background:" + WIN + ";color:#111;padding:0 5px;border-radius:5px;'>KO</span>" : "";
        sb.append("<td style='text-align:right;vertical-align:middle;width:215px;white-space:nowrap;'>")
                .append("<span style='font-size:18px;color:").append(jc).append(";'>").append(win ? "WIN" : "LOSE").append(koChip).append("</span>")
                .append(" <span style='font-size:14px;color:").append(jc).append(";'>").append(score).append("</span>")
                .append(meta.isEmpty() ? "" : " <span class='sub'>" + meta + "</span>")
                .append("</td></tr></table></div>");
        // 左右两个队伍块,各 2×2
        sb.append("<div style='padding:0 12px 10px;'><table><tr>")
                .append("<td style='width:50%;vertical-align:top;padding-right:5px;'>").append(teamBlock22("#15323a", C_MY, "kill.png", "death.png", team1)).append("</td>")
                .append("<td style='width:50%;vertical-align:top;padding-left:5px;'>").append(teamBlock22("#3a2030", C_EN, "kill2.png", "death2.png", team2)).append("</td>")
                .append("</tr></table></div></div>");
        return sb.toString();
    }

    /** 一个队伍块:2×2 共 4 人,带队色底 + 左色条。 */
    String teamBlock22(String tint, String color, String killIcon, String deathIcon, String[][] players) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='background:").append(tint).append(";border-left:4px solid ").append(color).append(";border-radius:10px;padding:6px 8px;'><table>");
        for (int r = 0; r < 2; r++) {
            sb.append("<tr>");
            for (int c = 0; c < 2; c++) {
                String[] p = players[r * 2 + c];
                sb.append("<td style='width:50%;'><table><tr>")
                        .append("<td style='width:32px;'>").append(img(p[0], 26, 26)).append("</td>")
                        .append("<td><div style='font-size:11px;'>").append(p[1]).append("</div>")
                        .append("<table style='margin-top:1px;'><tr>")
                        .append("<td style='width:40%;font-size:11px;white-space:nowrap;'>").append(img(killIcon, 17, 10)).append(" ").append(p[2]).append("</td>")
                        .append("<td style='width:28%;font-size:11px;white-space:nowrap;'>").append(img(deathIcon, 17, 10)).append(" ").append(p[3]).append("</td>")
                        .append("<td style='width:32%;font-size:11px;white-space:nowrap;'>").append(img(p[5], 13, 13)).append(" ").append(p[4]).append("</td>")
                        .append("</tr></table></td>")
                        .append("</tr></table></td>");
            }
            sb.append("</tr>");
        }
        return sb.append("</table></div>").toString();
    }

    /* --------------------------- 对战详情 --------------------------- */

    String battleDetailHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append(title("对战详情", "#128"));
        sb.append("<div class='card'>");
        // 头条:左模式/地图,右干净结果块
        sb.append("<div style='background:#e2402a;padding:14px 18px;'><table><tr>")
                .append("<td><div style='font-size:13px;color:#ffe;'>蛮颓比赛(开放) · 区域控制</div>")
                .append("<div style='font-size:28px;color:#fff;'>").append(img("rule.png", 26, 26)).append(" 真鲭跳台</div></td>")
                .append("<td style='text-align:right;width:200px;'>").append(resultBlock(true, true, "100 : 0", 30)).append("</td>")
                .append("</tr></table></div>");
        // 地图横幅(800x150 banner,与打工详情一致)
        sb.append("<div><img src='").append(ASSET).append("stage.png' style='width:100%;display:block;'/></div>");
        // meta chip 行(从头条里挪出来,不再混在右上)
        sb.append("<div style='padding:10px 18px 4px;'>")
                .append(chips("时长 2'42\"", "段位 S+0 → S+1", "+8p", "X-power 2483"))
                .append("</div>");
        // 奖牌
        sb.append("<div style='padding:4px 18px 10px;'>")
                .append(pill("大杀四方", WIN, "#111")).append(pill("涂地王", "#ffcf3f", "#111")).append(pill("救援王", "#7ec8ff", "#111"))
                .append("</div>");
        sb.append(teamBlock("己方队伍", "涂地率 53.2%", C_MY, true, new String[][]{
                {"w1.png","我","鲨鱼喷漆 / 防御↑·主+3","1280","12 (3)","4","sp1.png","3"},
                {"w2.png","あおい","卷管枪 / 回墨↑","1004","8 (1)","6","sp2.png","2"},
                {"w3.png","Koharu","开伞枪 / 人速↑","988","6 (4)","5","sp3.png","4"},
                {"w4.png","ナギ","桶 / 主电池","1120","10 (2)","7","sp4.png","3"}}));
        sb.append(teamBlock("敌方队伍", "涂地率 46.8%", C_EN, false, new String[][]{
                {"w5.png","Rio","喷枪 / 墨耗↓","900","5 (2)","8","sp1.png","2"},
                {"w6.png","つばさ","狙击 / 回墨↑","860","7 (1)","9","sp2.png","1"},
                {"w7.png","Leo","刷子 / 人速↑","780","4 (3)","10","sp3.png","3"},
                {"w8.png","ゆうき","滚筒 / 防御↑","1010","6 (0)","7","sp4.png","2"}}));
        sb.append("</div>");
        return sb.toString();
    }

    String teamBlock(String name, String rate, String color, boolean win, String[][] players) {
        StringBuilder sb = new StringBuilder("<div style='margin:0 14px 12px 14px;'>");
        sb.append("<table style='margin-bottom:4px;'><tr>")
                .append("<td style='border-left:6px solid ").append(color).append(";padding-left:8px;font-size:15px;color:").append(color).append(";'>").append(name).append("</td>")
                .append("<td style='text-align:right;' class='sub'>").append(rate).append("</td></tr></table>");
        for (String[] p : players) {
            String jc = win ? WIN : INK;
            sb.append("<table style='background:").append(CARD2).append(";border-radius:10px;margin-bottom:5px;'><tr>")
                    .append("<td style='width:46px;padding:6px;'>").append(img(p[0], 36, 36)).append("</td>")
                    .append("<td><div style='font-size:13px;'>").append(p[1]).append("</div><div class='sub'>").append(p[2]).append("</div></td>")
                    .append(col("涂地", p[3], INK)).append(col("K(A)", p[4], jc)).append(col("D", p[5], INK))
                    .append("<td style='width:54px;text-align:center;'>").append(img(p[6], 20, 20)).append(" ").append(p[7]).append("</td>")
                    .append("</tr></table>");
        }
        return sb.append("</div>").toString();
    }

    String col(String label, String val, String color) {
        return "<td style='width:64px;text-align:center;'><span class='sub'>" + label + "</span><br/><span style='font-size:14px;color:" + color + ";'>" + val + "</span></td>";
    }

    /* --------------------------- 打工列表 --------------------------- */

    String coopListHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append(title("打工记录", "账号1 · 最近 5 场"));
        // 队员数据: {名字, 击倒, 送金蛋, 金蛋助攻, 送红蛋, 救援, 被救}
        // boss 数据: {boss图, boss名, 金鳞, 银鳞, 铜鳞};无 boss 传 null
        sb.append(coopListCard("新卷堡","巨大跑·BIG RUN","20:10",true,"W3","333","达人+3 ↑","128","89",
                new String[]{"cw1.png","cw2.png","cw3.png","cw4.png"},
                "W1 28·W2 24·W3 31",
                new String[]{"boss.png","横纲鲑","3","5","12"},
                new String[][]{{"我","28","32","5","30","3","1"},{"Mako","21","26","3","24","2","2"},{"りん","19","20","2","18","1","0"},{"Tao","24","29","4","27","4","2"}}));
        sb.append(coopListCard("时不知鲑烟熏工房","普通打工","19:42",false,"W2","120","达人 ↓","64","51",
                new String[]{"cw2.png","cw3.png","cw1.png","cw4.png"},
                "W1 26·W2 38✗",
                null,
                new String[][]{{"我","15","18","2","16","2","3"},{"Sora","12","14","1","13","1","2"},{"Mei","9","11","1","10","0","1"},{"Kai","13","16","2","15","3","1"}}));
        sb.append(coopListCard("鲑舟瀑布","团队打工","18:30",true,"W3","220","达人+1 ↑","102","73",
                new String[]{"cw3.png","cw1.png","cw4.png","cw2.png"},
                "W1 24·W2 26·W3 23",
                null,
                new String[][]{{"我","22","27","4","25","2","1"},{"Aki","18","21","2","19","1","1"},{"Yo","20","24","3","22","3","0"},{"Ui","17","19","1","18","2","2"}}));
        sb.append(coopListCard("难破船Don鲷","巨大跑·BIG RUN","17:05",true,"W3","333","传说+50 ↑","145","98",
                new String[]{"cw4.png","cw2.png","cw1.png","cw3.png"},
                "W1 31·W2 34·W3 33",
                new String[]{"boss2.png","辰龙","5","8","15"},
                new String[][]{{"我","31","35","6","32","4","0"},{"Ken","26","30","4","28","3","2"},{"Sho","24","28","3","26","2","1"},{"Rei","28","32","5","30","3","1"}}));
        sb.append(coopListCard("醋饭海洋","普通打工","16:20",false,"W1","90","胜+2 ↓","28","19",
                new String[]{"cw1.png","cw3.png","cw2.png","cw4.png"},
                "W1 28✗",
                null,
                new String[][]{{"我","8","9","1","8","0","2"},{"Hina","6","7","1","6","1","1"},{"Tao","5","6","0","5","0","1"},{"Mei","7","8","1","7","1","0"}}));
        return sb.toString();
    }

    /** 打工列表卡片:地图缩略图 + 整体配枪 + 危险度/金红蛋 + Wave 概要 + (有则)boss图&鳞片 + 4 名队员(独立面板,列对齐)。 */
    String coopListCard(String stage, String mode, String time, boolean clear, String waveText,
                        String danger, String grade, String gold, String red,
                        String[] supplyWeapons, String waveInfo, String[] boss, String[][] players) {
        String accent = "#e07b00";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='card' style='border-left:7px solid ").append(accent).append(";'>");
        // 头部:地图/模式/时间 | 地图缩略图(中) | Clear+波数+评价
        sb.append("<div style='padding:6px 14px 2px;'><table><tr>");
        sb.append("<td style='vertical-align:middle;'>")
                .append("<span style='font-size:18px;'>").append(stage).append("</span>")
                .append(" <span class='sub'>").append(mode).append(" · ").append(time).append("</span></td>");
        sb.append("<td style='width:140px;text-align:center;vertical-align:middle;'>").append(img("coopstage.png", 124, 23)).append("</td>");
        sb.append("<td style='text-align:right;vertical-align:middle;width:200px;white-space:nowrap;'>")
                .append("<span style='font-size:17px;color:").append(clear ? WIN : LOSE).append(";'>").append(clear ? "Clear" : "Fail")
                .append(" ").append(waveText).append("</span>")
                .append(" <span style='font-size:12px;color:#ffcf3f;'>").append(grade).append("</span></td>")
                .append("</tr></table></div>");
        // 配枪 + 危险度 + Wave概要(整合进同一行) +（有则）boss图&鳞片 + 金红蛋(放最后)
        sb.append("<div style='padding:0 14px 5px;'><table><tr>");
        // 4 把配枪放进固定 4 格的内嵌表格,避免 openhtmltopdf 对内联图片换行
        sb.append("<td style='width:150px;'><table style='width:auto;'><tr>");
        for (String w : supplyWeapons) sb.append("<td style='width:36px;text-align:center;'>").append(img(w, 28, 28)).append("</td>");
        sb.append("</tr></table></td>");
        sb.append("<td style='font-size:11px;white-space:nowrap;'><span style='color:#ffcf3f;'>危险度").append(danger).append("%</span>&#160;&#160;<span class='sub'>").append(waveInfo).append("</span></td>");
        sb.append("<td style='text-align:right;white-space:nowrap;'>");
        if (boss != null) {
            sb.append(img(boss[0], 26, 26)).append(" <span style='font-size:12px;color:").append(WIN).append(";'>").append(boss[1]).append("</span> ")
                    .append(img("scale_gold.png", 15, 15)).append(boss[2]).append(" ")
                    .append(img("scale_silver.png", 15, 15)).append(boss[3]).append(" ")
                    .append(img("scale_bronze.png", 15, 15)).append(boss[4]).append("&#160;&#160;&#160;");
        }
        sb.append(img("egg_gold.png", 18, 18)).append(" ").append(gold).append("&#160;&#160;")
                .append(img("egg_red.png", 18, 18)).append(" ").append(red);
        sb.append("</td></tr></table></div>");
        // 队员:独立面板,名字独占一列可截断(击倒在右列,不贴名字),其下 金蛋(助攻)/红蛋、救援/被救 两列对齐
        sb.append("<div style='padding:2px 12px 8px;'><table><tr>");
        for (String[] p : players) {
            String pbg = "我".equals(p[0]) ? "#2c365e" : CARD2;
            sb.append("<td style='width:25%;vertical-align:top;padding:0 3px;'>")
                    .append("<div style='background:").append(pbg).append(";border-radius:8px;padding:5px 9px;'>")
                    .append("<table><tr><td style='font-size:12px;white-space:nowrap;overflow:hidden;'>").append(p[0]).append("</td>")
                    .append("<td style='text-align:right;font-size:11px;white-space:nowrap;'>").append(img("kill.png", 16, 10)).append(" ").append(p[1]).append("</td></tr></table>")
                    .append("<table style='margin-top:3px;'>")
                    .append("<tr><td style='width:50%;font-size:11px;white-space:nowrap;'>").append(img("egg_gold.png", 15, 15)).append(" ").append(p[2]).append("<span class='sub'>(").append(p[3]).append(")</span></td>")
                    .append("<td style='width:50%;font-size:11px;white-space:nowrap;'>").append(img("egg_red.png", 15, 15)).append(" ").append(p[4]).append("</td></tr>")
                    .append("<tr><td style='font-size:11px;white-space:nowrap;'>").append(img("rescue.png", 16, 12)).append(" ").append(p[5]).append("</td>")
                    .append("<td style='font-size:11px;white-space:nowrap;'>").append(img("rescued.png", 16, 12)).append(" ").append(p[6]).append("</td></tr>")
                    .append("</table></div></td>");
        }
        sb.append("</tr></table></div></div>");
        return sb.toString();
    }

    /* --------------------------- 打工详情 --------------------------- */

    String coopDetailHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append(title("打工详情", "#56"));
        sb.append("<div class='card'>");
        // 头条:模式 + 地图名 + Clear/危险度/评价
        sb.append("<div style='background:#e07b00;padding:14px 18px;'><table><tr>")
                .append("<td><div style='font-size:13px;color:#ffe;'>巨大跑 · BIG RUN</div><div style='font-size:26px;color:#fff;'>新卷堡</div></td>")
                .append("<td style='text-align:right;'><div style='font-size:24px;color:").append(WIN).append(";'>Clear <span style='font-size:13px;color:#fff;'>W3</span></div>")
                .append("<div style='font-size:14px;color:#fff;'>危险度 333% · 达人+3 ↑</div></td>")
                .append("</tr></table></div>");
        // 地图横幅(800x150 banner)
        sb.append("<div><img src='").append(ASSET).append("coopstage.png' style='width:100%;display:block;'/></div>");
        // 统计条:得分/熊点/金红蛋
        sb.append("<div style='padding:12px 18px 4px;'><table style='background:").append(CARD2).append(";border-radius:10px;'><tr>")
                .append(kv("得分", "1240")).append(kv("熊点", "+480")).append(kv("金蛋", "128")).append(kv("红蛋", "89"))
                .append("</tr></table></div>");
        // 气味计(满了才会在 W3 后出头目)
        sb.append("<div style='padding:4px 18px 8px;'><div class='sub'>头目鲑鱼气味计（满则 W3 后出现）</div>").append(bar(100)).append("</div>");
        // waves
        sb.append("<div style='padding:0 18px 0;'>");
        sb.append(waveRow("W1", "满潮", "25", "28", ""));
        sb.append(waveRow("W2", "普通", "21", "24", ""));
        sb.append(waveRow("W3", "异常 · 泥潭", "30", "31", "大招 鲸鲨炮 ×2"));
        // EX 头目鲑鱼(条件:气味计满才出现),boss 图 + 鳞片 接在 W3 后
        sb.append("<table style='background:#2c365e;border-radius:10px;margin-bottom:5px;'><tr>")
                .append("<td style='width:64px;text-align:center;padding:4px;'>").append(img("boss.png", 48, 48)).append("</td>")
                .append("<td><div style='font-size:15px;color:").append(WIN).append(";'>EX · 横纲鲑 <span style='font-size:12px;color:#fff;'>讨伐成功</span></div></td>")
                .append("<td style='text-align:right;padding-right:10px;white-space:nowrap;'>")
                .append(img("scale_gold.png", 20, 20)).append(" 3&#160;&#160;")
                .append(img("scale_silver.png", 20, 20)).append(" 5&#160;&#160;")
                .append(img("scale_bronze.png", 20, 20)).append(" 12")
                .append("</td></tr></table>");
        sb.append("</div>");
        // 队员
        sb.append("<div style='padding:8px 18px 4px;'><div style='font-size:14px;color:#ffcf3f;'>队员表现</div></div><div style='padding:0 14px 14px;'>");
        sb.append(coopPlayer("我","28","32(5)","30","3","1",true));
        sb.append(coopPlayer("Mako","21","26(3)","24","2","2",false));
        sb.append(coopPlayer("りん","19","20(2)","18","1","0",false));
        sb.append(coopPlayer("Tao","24","29(4)","27","4","2",false));
        sb.append("</div>");
        sb.append("<div style='padding:0 18px 16px;'><div style='font-size:13px;color:#ffcf3f;margin-bottom:4px;'>敌人击倒</div>")
                .append("<span class='sub'>鲑鱼 12&#160;&#160;蝙蝠 8&#160;&#160;铁壁 5&#160;&#160;高塔 3&#160;&#160;飞鱼 4&#160;&#160;蛇 2</span></div>");
        sb.append("</div>");
        return sb.toString();
    }

    String waveRow(String wave, String event, String norm, String got, String right) {
        String r = right.isEmpty() ? "" : "<span class='sub'>" + right + "</span>";
        return "<table style='background:" + CARD2 + ";border-radius:10px;margin-bottom:5px;'><tr>"
                + "<td style='width:48px;text-align:center;font-size:15px;color:" + WIN + ";'>" + wave + "</td>"
                + "<td style='width:130px;' class='sub'>" + event + "</td>"
                + "<td><span class='sub'>达标</span> " + norm + "&#160;&#160;<span class='sub'>送蛋</span> " + got + "</td>"
                + "<td style='text-align:right;padding-right:10px;'>" + r + "</td></tr></table>";
    }

    String coopPlayer(String name, String defeat, String gold, String red, String rescue, String rescued, boolean me) {
        return "<table style='background:" + (me ? "#2c365e" : CARD2) + ";border-radius:10px;margin-bottom:5px;'><tr>"
                + "<td style='width:90px;padding:6px 0 6px 12px;font-size:13px;'>" + name + "</td>"
                + iconCell("kill.png", 17, 10, "击倒", defeat)
                + iconCell("egg_gold.png", 16, 16, "金蛋", gold)
                + iconCell("egg_red.png", 16, 16, "红蛋", red)
                + iconCell("rescue.png", 17, 13, "救援", rescue)
                + iconCell("rescued.png", 17, 13, "被救", rescued)
                + "</tr></table>";
    }

    /** 居中的“图标+标签 / 数值”列,对齐用。 */
    String iconCell(String icon, int iw, int ih, String label, String val) {
        return "<td style='width:100px;text-align:center;'>"
                + "<div class='sub'>" + img(icon, iw, ih) + " " + label + "</div>"
                + "<div style='font-size:15px;'>" + val + "</div></td>";
    }

    String ccell(String label, String val) {
        return "<td style='width:90px;text-align:center;'><span class='sub'>" + label + "</span><br/><span style='font-size:14px;'>" + val + "</span></td>";
    }

    String kv(String k, String v) {
        return "<td style='text-align:center;padding:8px;'><div class='sub'>" + k + "</div><div style='font-size:16px;'>" + v + "</div></td>";
    }

    String pill(String text, String bg, String fg) {
        return "<span style='background:" + bg + ";color:" + fg + ";font-size:12px;padding:3px 10px;border-radius:10px;margin-right:8px;'>" + text + "</span>";
    }

    String bar(int pct) {
        return "<div style='background:#11152a;border-radius:6px;height:12px;margin-top:4px;'>"
                + "<div style='background:" + WIN + ";width:" + pct + "%;height:12px;border-radius:6px;'>&#160;</div></div>";
    }
}
