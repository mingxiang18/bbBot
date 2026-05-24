package com.bb.bot.handler.splatoon.render;

import com.alibaba.fastjson2.JSONObject;
import com.bb.bot.common.util.FileUtils;
import com.bb.bot.common.util.ImageUtils;
import com.bb.bot.common.util.ResourcesUtils;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 把 {@code BbSplatoonUserHandler.getCoopPoint} 内联的 130 行渲染段迁出来。
 * 接 {@code coopResult} JSON（包含 pointCard + scale 两段），返回写好像素的 PNG 文件。
 *
 * <p>字段位置 / 颜色 / 字号 / 图标全部逐字搬，保持像素一致。
 *
 * @author ren
 */
@Component
public class CoopPointRenderer {

    @Autowired
    private ResourcesUtils resourcesUtils;

    @SneakyThrows
    public File render(JSONObject coopResult) {
        JSONObject pointCard = coopResult.getJSONObject("pointCard");
        JSONObject scaleData = coopResult.getJSONObject("scale");

        File backgroundImage = resourcesUtils.getStaticResource("splatoon/background/bg_good2.jpg");
        File imageFile = FileUtils.buildTmpFile();
        ImageUtils.cropImage(backgroundImage, imageFile, 0, 0, 360, 460);
        BufferedImage image = ImageIO.read(imageFile);
        Graphics2D g2d = ImageUtils.createDefaultG2dFromFile(image);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 28, new Color(255, 48, 20),
                "熊先生点数卡",
                30, 40,
                200, 30,
                0);

        ImageUtils.createRoundRectOnImage(g2d, new Color(255, 115, 0), 24, 60, 312, 380, 0.5f);

        ImageUtils.createRoundRectOnImage(g2d, new Color(255, 218, 0), 28, 70, 300, 28, 0.3f);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                "累计点数: " + pointCard.getInteger("totalPoint"),
                30, 90,
                200, 30,
                0);

        ImageUtils.createRoundRectOnImage(g2d, Color.BLACK, 28, 102, 340, 160, 0.1f);
        Color wordColor = new Color(223, 223, 223, 255);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "打工次数: " + pointCard.getInteger("playCount"),
                30, 120,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "已收集的金鲑鱼卵: " + pointCard.getInteger("goldenDeliverCount"),
                30, 150,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "已收集的鲑鱼卵: " + pointCard.getInteger("deliverCount"),
                30, 180,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "已击倒的头目鲑鱼: " + pointCard.getInteger("defeatBossCount"),
                30, 210,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, wordColor,
                "救援次数: " + pointCard.getInteger("rescueCount"),
                30, 240,
                400, 30,
                0);

        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 26, Color.GREEN,
                "鳞片",
                30, 290,
                400, 30,
                0);

        File bronzeScale = resourcesUtils.getStaticResource("nso_splatoon/coop/icon/bronze_scale.png");
        ImageUtils.mergeImageToOtherImage(g2d, bronzeScale, 50, 310, 60, 60);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                "x" + scaleData.getInteger("bronze"),
                60, 390,
                200, 30,
                0);

        File sliverScale = resourcesUtils.getStaticResource("nso_splatoon/coop/icon/sliver_scale.png");
        ImageUtils.mergeImageToOtherImage(g2d, sliverScale, 146, 310, 60, 60);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                "x" + scaleData.getInteger("silver"),
                156, 390,
                200, 30,
                0);

        File goldScale = resourcesUtils.getStaticResource("nso_splatoon/coop/icon/gold_scale.png");
        ImageUtils.mergeImageToOtherImage(g2d, goldScale, 240, 310, 60, 60);
        ImageUtils.writeWordInImage(g2d,
                resourcesUtils.getStaticResource("font/sakura.ttf"), Font.PLAIN, 22, Color.YELLOW,
                "x" + scaleData.getInteger("gold"),
                250, 390,
                200, 30,
                0);

        ImageUtils.writeG2dToFile(g2d, image, imageFile);
        return imageFile;
    }
}
