package com.bb.bot.common.util;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T7.2：{@link ImageUtils#createTranslucentCanvas(int, int)} 提取后的 golden-image 等价校验。
 *
 * <p>该方法收敛了此前散落在 {@code enlargementImageEqualProportion}（两个重载）与
 * {@code resizeImage} 三处的「两行透明初始化」逻辑：
 * <pre>
 *   BufferedImage tag = new BufferedImage(w, h, TYPE_INT_RGB);
 *   Graphics2D g = tag.createGraphics();
 *   tag = g.getDeviceConfiguration().createCompatibleImage(w, h, TRANSLUCENT);
 *   g = tag.createGraphics();
 * </pre>
 *
 * <p>重点验证：透明 PNG 缩放后不变黑（保留透明像素），且重构后输出与「重构前原始内联表达式」逐像素等价（D2 容差）。
 */
class ImageUtilsTranslucentTest {

    /** D2 容差：逐像素 RGB（含 alpha）差 <= 2，或差异像素占比 < 0.5%。 */
    private static final int CHANNEL_TOLERANCE = 2;
    private static final double DIFF_PIXEL_RATIO = 0.005;

    /**
     * 重构前的原始内联表达式（逐字符复刻三处共用的两行透明初始化），作为 golden 参考实现。
     */
    private static BufferedImage legacyTranslucentCanvas(int width, int height) {
        BufferedImage tag = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = tag.createGraphics();
        // 下面两行解决png透明图片会变黑的问题
        tag = g.getDeviceConfiguration().createCompatibleImage(tag.getWidth(null), tag.getHeight(null), Transparency.TRANSLUCENT);
        g = tag.createGraphics();
        g.dispose();
        return tag;
    }

    @Test
    void createTranslucentCanvas_supportsAlpha() {
        BufferedImage canvas = ImageUtils.createTranslucentCanvas(40, 30);
        assertThat(canvas.getWidth()).isEqualTo(40);
        assertThat(canvas.getHeight()).isEqualTo(30);
        // 必须支持透明（否则缩放透明 png 会变黑）
        assertThat(canvas.getColorModel().hasAlpha())
                .as("translucent canvas must support alpha")
                .isTrue();
        assertThat(canvas.getTransparency()).isEqualTo(Transparency.TRANSLUCENT);
        // 全空画布像素 alpha 应为 0（完全透明）
        assertThat(canvas.getRGB(0, 0) >>> 24).isZero();
    }

    @Test
    void createTranslucentCanvas_equivalentToLegacyInlineExpression() {
        for (int[] dim : new int[][]{{1, 1}, {10, 10}, {64, 48}, {200, 1}, {1, 200}}) {
            BufferedImage expected = legacyTranslucentCanvas(dim[0], dim[1]);
            BufferedImage actual = ImageUtils.createTranslucentCanvas(dim[0], dim[1]);
            assertEquivalent(expected, actual, dim[0] + "x" + dim[1]);
        }
    }

    /**
     * golden 流程：透明 PNG 缩放（{@link ImageUtils#enlargementImageEqualProportion(String, double)}）
     * 后必须保留透明，绝不变黑。
     */
    @Test
    void enlargeTransparentPng_doesNotTurnBlack(@TempDir Path tmp) throws Exception {
        File src = writeTransparentSamplePng(tmp);

        BufferedImage scaled = ImageUtils.enlargementImageEqualProportion(src.getAbsolutePath(), 2.0d);

        assertThat(scaled.getColorModel().hasAlpha()).isTrue();
        // 透明角落（src 左上角是透明的）放大后仍透明，而非变成黑色不透明像素
        assertThat(scaled.getRGB(0, 0) >>> 24)
                .as("top-left should remain transparent, not black-opaque")
                .isZero();
        // 不应整图变黑：至少存在非黑且不透明的内容像素
        assertThat(hasOpaqueColoredPixel(scaled))
                .as("scaled image must retain colored content")
                .isTrue();
    }

    /**
     * golden 流程：透明 PNG 通过 {@link ImageUtils#resizeImage(String, int, int)} 缩放同样不变黑。
     */
    @Test
    void resizeTransparentPng_doesNotTurnBlack(@TempDir Path tmp) throws Exception {
        File src = writeTransparentSamplePng(tmp);

        BufferedImage resized = ImageUtils.resizeImage(src.getAbsolutePath(), 32, 32);

        assertThat(resized.getColorModel().hasAlpha()).isTrue();
        assertThat(resized.getRGB(0, 0) >>> 24)
                .as("top-left should remain transparent, not black-opaque")
                .isZero();
        assertThat(hasOpaqueColoredPixel(resized)).isTrue();
    }

    /**
     * golden 流程：带透明度 alpha 的缩放重载亦走透明画布，结果与 legacy 画布同样支持透明。
     */
    @Test
    void enlargeWithAlpha_usesTranslucentCanvas(@TempDir Path tmp) throws Exception {
        File src = writeTransparentSamplePng(tmp);

        BufferedImage scaled = ImageUtils.enlargementImageEqualProportion(src.getAbsolutePath(), 1.5d, 0.5f);

        assertThat(scaled.getColorModel().hasAlpha()).isTrue();
        assertThat(scaled.getRGB(0, 0) >>> 24)
                .as("transparent corner stays transparent under alpha composite")
                .isZero();
    }

    // ---- helpers ----

    /** 生成一张左上半透明、其余画了彩色矩形的样本透明 PNG。 */
    private static File writeTransparentSamplePng(Path tmp) throws Exception {
        int w = 16, h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, w, h); // 全透明
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new java.awt.Color(220, 30, 40, 255)); // 不透明红
        g.fillRect(w / 2, h / 2, w / 2, h / 2); // 右下角填红，左上角保持透明
        g.dispose();

        File f = Files.createTempFile(tmp, "sample", ".png").toFile();
        ImageIO.write(img, "png", f);
        return f;
    }

    private static boolean hasOpaqueColoredPixel(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = argb >>> 24;
                int r = (argb >> 16) & 0xFF;
                int gg = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (a > 0 && (r > 10 || gg > 10 || b > 10)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** D2 容差比较两张图。 */
    private static void assertEquivalent(BufferedImage expected, BufferedImage actual, String label) {
        assertThat(actual.getWidth()).as("width %s", label).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).as("height %s", label).isEqualTo(expected.getHeight());

        long diffPixels = 0;
        long total = (long) expected.getWidth() * expected.getHeight();
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int ep = expected.getRGB(x, y);
                int ap = actual.getRGB(x, y);
                if (!withinTolerance(ep, ap)) {
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
