package com.bb.bot.common.util;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T7.3：{@link ImageUtils#joinImagesHorizontal} / {@link ImageUtils#joinImagesVertical}
 * 收敛为私有 {@code joinImages(..., boolean horizontal)} 后的 golden-image 等价校验。
 *
 * <p>golden 流程：以「重构前的原始内联实现」（{@link #legacyJoinHorizontal}/{@link #legacyJoinVertical}，
 * 逐字符复刻旧方法体）渲染参考图，再用重构后的 public 方法渲染实际图，按 D2 容差逐像素比对。
 *
 * <p>注意：旧实现读取第二张图时复用第一张的 width/height（{@code imageTwo.getRGB(0,0,width,height,...)}），
 * 因此样本图必须等尺寸——这是被刻意保留的等价行为，参考实现与重构实现同样如此。
 */
class ImageUtilsJoinTest {

    /** D2 容差：逐像素 RGB 差 <= 2，或差异像素占比 < 0.5%。 */
    private static final int CHANNEL_TOLERANCE = 2;
    private static final double DIFF_PIXEL_RATIO = 0.005;

    @Test
    void joinImagesHorizontal_equivalentToLegacy(@TempDir Path tmp) throws Exception {
        File a = writeSample(tmp, "a", 24, 18, new Color(220, 30, 40));
        File b = writeSample(tmp, "b", 24, 18, new Color(20, 120, 220));

        File golden = new File(tmp.toFile(), "golden_h.png");
        legacyJoinHorizontal(a.getAbsolutePath(), b.getAbsolutePath(), "png", golden.getAbsolutePath());

        File actual = new File(tmp.toFile(), "actual_h.png");
        ImageUtils.joinImagesHorizontal(a.getAbsolutePath(), b.getAbsolutePath(), "png", actual.getAbsolutePath());

        BufferedImage expected = ImageIO.read(golden);
        BufferedImage got = ImageIO.read(actual);
        // 维度：横向应为 width*2 x height
        assertThat(got.getWidth()).isEqualTo(48);
        assertThat(got.getHeight()).isEqualTo(18);
        assertEquivalent(expected, got, "horizontal");
    }

    @Test
    void joinImagesVertical_equivalentToLegacy(@TempDir Path tmp) throws Exception {
        File a = writeSample(tmp, "a", 24, 18, new Color(220, 30, 40));
        File b = writeSample(tmp, "b", 24, 18, new Color(20, 120, 220));

        File golden = new File(tmp.toFile(), "golden_v.png");
        legacyJoinVertical(a.getAbsolutePath(), b.getAbsolutePath(), "png", golden.getAbsolutePath());

        File actual = new File(tmp.toFile(), "actual_v.png");
        ImageUtils.joinImagesVertical(a.getAbsolutePath(), b.getAbsolutePath(), "png", actual.getAbsolutePath());

        BufferedImage expected = ImageIO.read(golden);
        BufferedImage got = ImageIO.read(actual);
        // 维度：纵向应为 width x height*2
        assertThat(got.getWidth()).isEqualTo(24);
        assertThat(got.getHeight()).isEqualTo(36);
        assertEquivalent(expected, got, "vertical");
    }

    // ---- legacy 参考实现（逐字符复刻重构前的方法体）----

    private static void legacyJoinHorizontal(String firstSrcImagePath, String secondSrcImagePath, String imageFormat, String toPath) {
        try {
            File fileOne = new File(firstSrcImagePath);
            BufferedImage imageOne = ImageIO.read(fileOne);
            int width = imageOne.getWidth();
            int height = imageOne.getHeight();
            int[] imageArrayOne = new int[width * height];
            imageArrayOne = imageOne.getRGB(0, 0, width, height, imageArrayOne, 0, width);

            File fileTwo = new File(secondSrcImagePath);
            BufferedImage imageTwo = ImageIO.read(fileTwo);
            int width2 = imageTwo.getWidth();
            int height2 = imageTwo.getHeight();
            int[] ImageArrayTwo = new int[width2 * height2];
            ImageArrayTwo = imageTwo.getRGB(0, 0, width, height, ImageArrayTwo, 0, width);

            BufferedImage imageNew = new BufferedImage(width * 2, height, BufferedImage.TYPE_INT_RGB);
            imageNew.setRGB(0, 0, width, height, imageArrayOne, 0, width);
            imageNew.setRGB(width, 0, width, height, ImageArrayTwo, 0, width);

            File outFile = new File(toPath);
            ImageIO.write(imageNew, imageFormat, outFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void legacyJoinVertical(String firstSrcImagePath, String secondSrcImagePath, String imageFormat, String toPath) {
        try {
            File fileOne = new File(firstSrcImagePath);
            BufferedImage imageOne = ImageIO.read(fileOne);
            int width = imageOne.getWidth();
            int height = imageOne.getHeight();
            int[] imageArrayOne = new int[width * height];
            imageArrayOne = imageOne.getRGB(0, 0, width, height, imageArrayOne, 0, width);

            File fileTwo = new File(secondSrcImagePath);
            BufferedImage imageTwo = ImageIO.read(fileTwo);
            int width2 = imageTwo.getWidth();
            int height2 = imageTwo.getHeight();
            int[] ImageArrayTwo = new int[width2 * height2];
            ImageArrayTwo = imageTwo.getRGB(0, 0, width, height, ImageArrayTwo, 0, width);

            BufferedImage imageNew = new BufferedImage(width, height * 2, BufferedImage.TYPE_INT_RGB);
            imageNew.setRGB(0, 0, width, height, imageArrayOne, 0, width);
            imageNew.setRGB(0, height, width, height, ImageArrayTwo, 0, width);

            File outFile = new File(toPath);
            ImageIO.write(imageNew, imageFormat, outFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---- helpers ----

    /** 生成一张左上角填底色、对角线另一色的样本图，便于检出错位/翻转。 */
    private static File writeSample(Path tmp, String name, int w, int h, Color base) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(base);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255 - base.getRed(), 255 - base.getGreen(), 255 - base.getBlue()));
        g.fillRect(w / 2, h / 2, w / 2, h / 2);
        g.dispose();
        File f = new File(tmp.toFile(), name + ".png");
        ImageIO.write(img, "png", f);
        return f;
    }

    private static void assertEquivalent(BufferedImage expected, BufferedImage actual, String label) {
        assertThat(actual.getWidth()).as("width %s", label).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).as("height %s", label).isEqualTo(expected.getHeight());

        long diffPixels = 0;
        long total = (long) expected.getWidth() * expected.getHeight();
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if (!withinTolerance(expected.getRGB(x, y), actual.getRGB(x, y))) {
                    diffPixels++;
                }
            }
        }
        double ratio = total == 0 ? 0d : (double) diffPixels / total;
        assertThat(ratio)
                .as("diff pixel ratio for %s (diff=%d/%d)", label, diffPixels, total)
                .isLessThan(DIFF_PIXEL_RATIO + 1e-9);
    }

    private static boolean withinTolerance(int argb1, int argb2) {
        for (int shift = 0; shift <= 24; shift += 8) {
            int c1 = (argb1 >> shift) & 0xFF;
            int c2 = (argb2 >> shift) & 0xFF;
            if (Math.abs(c1 - c2) > CHANNEL_TOLERANCE) {
                return false;
            }
        }
        return true;
    }
}
