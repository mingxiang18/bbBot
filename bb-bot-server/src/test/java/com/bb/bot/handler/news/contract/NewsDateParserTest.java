package com.bb.bot.handler.news.contract;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NewsDateParser} 单测：覆盖中英文 RSS 常见日期格式（设计要求 #7）。
 */
class NewsDateParserTest {

    @Test
    void parses_rfc1123_gmt() {
        // GMT → 折算到本地（这里只断言能解析出非空且年月对）
        LocalDateTime t = NewsDateParser.parse("Mon, 30 May 2026 08:00:00 GMT");
        assertThat(t).isNotNull();
        assertThat(t.getYear()).isEqualTo(2026);
        assertThat(t.getMonthValue()).isEqualTo(5);
    }

    @Test
    void parses_rfc1123_offset() {
        LocalDateTime t = NewsDateParser.parse("Tue, 03 Jun 2026 08:12:00 +0800");
        assertThat(t).isNotNull();
        assertThat(t.getYear()).isEqualTo(2026);
    }

    @Test
    void parses_iso_offset() {
        LocalDateTime t = NewsDateParser.parse("2026-06-03T08:12:00+08:00");
        assertThat(t).isNotNull();
        assertThat(t.getYear()).isEqualTo(2026);
        assertThat(t.getDayOfMonth()).isEqualTo(3);
    }

    @Test
    void parses_iso_zulu() {
        assertThat(NewsDateParser.parse("2026-06-03T00:12:00Z")).isNotNull();
    }

    @Test
    void parses_plain_local_datetime() {
        LocalDateTime t = NewsDateParser.parse("2026-06-03 08:12:00");
        assertThat(t).isEqualTo(LocalDateTime.of(2026, 6, 3, 8, 12, 0));
    }

    @Test
    void parses_date_only() {
        LocalDateTime t = NewsDateParser.parse("2026-06-03");
        assertThat(t).isEqualTo(LocalDateTime.of(2026, 6, 3, 0, 0, 0));
    }

    @Test
    void returnsNull_forBlankOrGarbage() {
        assertThat(NewsDateParser.parse(null)).isNull();
        assertThat(NewsDateParser.parse("  ")).isNull();
        assertThat(NewsDateParser.parse("不是日期")).isNull();
    }
}
