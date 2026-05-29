package com.bb.bot.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T7.1：{@link ImageUtils#getExtension(String)} 边界覆盖。
 * 该方法替换了原先 7 处 {@code name.substring(name.lastIndexOf(".") + 1)},
 * 必须与原表达式逐字符等价。
 */
class ImageUtilsTest {

    @Test
    void getExtension_withDot_returnsAfterLastDot() {
        assertThat(ImageUtils.getExtension("image.png")).isEqualTo("png");
        assertThat(ImageUtils.getExtension("photo.JPEG")).isEqualTo("JPEG");
    }

    @Test
    void getExtension_noDot_returnsWholeName() {
        // 无点：lastIndexOf 返回 -1，substring(0) 即原串
        assertThat(ImageUtils.getExtension("noextension")).isEqualTo("noextension");
    }

    @Test
    void getExtension_multipleDots_returnsAfterLastDot() {
        assertThat(ImageUtils.getExtension("archive.tar.gz")).isEqualTo("gz");
        assertThat(ImageUtils.getExtension("a.b.c.d.e")).isEqualTo("e");
    }

    @Test
    void getExtension_trailingDot_returnsEmpty() {
        // 末尾为点：lastIndexOf 指向最后一位，substring(len) 为空串
        assertThat(ImageUtils.getExtension("filename.")).isEmpty();
    }

    @Test
    void getExtension_emptyString_returnsEmpty() {
        // 空串：lastIndexOf 返回 -1，substring(0) 即空串
        assertThat(ImageUtils.getExtension("")).isEmpty();
    }

    @Test
    void getExtension_equivalentToOriginalExpression() {
        // 直接对照原表达式，证明等价重构
        for (String name : new String[]{
                "image.png", "noextension", "archive.tar.gz",
                "a.b.c.d.e", "filename.", "", ".hidden", "."}) {
            String expected = name.substring(name.lastIndexOf(".") + 1);
            assertThat(ImageUtils.getExtension(name))
                    .as("name=[%s]", name)
                    .isEqualTo(expected);
        }
    }
}
