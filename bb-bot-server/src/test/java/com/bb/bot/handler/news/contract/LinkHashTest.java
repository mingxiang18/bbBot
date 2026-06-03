package com.bb.bot.handler.news.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LinkHash} 单测：Phase 4 canonical 白名单——剔除追踪参、保留业务参。
 */
class LinkHashTest {

    @Test
    void stripsTrackingParams_keepsBusinessParams() {
        // utm_*/from/spm 等追踪参被剔除，id 业务参保留
        String a = LinkHash.normalize("https://site.com/a?id=123&utm_source=x&utm_medium=y&from=feed");
        assertThat(a).isEqualTo("https://site.com/a?id=123");
    }

    @Test
    void businessQueryIdNotStripped_distinctFromNoQuery() {
        // 以 query 作为文章 ID 的源：?id=123 与 ?id=456 必须是不同 hash（旧实现会误并）
        String h1 = LinkHash.of("https://site.com/read?id=123");
        String h2 = LinkHash.of("https://site.com/read?id=456");
        String hNo = LinkHash.of("https://site.com/read");
        assertThat(h1).isNotEqualTo(h2);
        assertThat(h1).isNotEqualTo(hNo);
    }

    @Test
    void paramOrderIndependent_sameHash() {
        // 保留参按 key 排序 → 参数顺序不影响去重键
        assertThat(LinkHash.of("https://s.com/a?b=2&a=1"))
                .isEqualTo(LinkHash.of("https://s.com/a?a=1&b=2"));
    }

    @Test
    void dropsFragment_andPureTrackingQueryBecomesBare() {
        assertThat(LinkHash.normalize("https://s.com/a?utm_source=x#frag"))
                .isEqualTo("https://s.com/a");
        assertThat(LinkHash.normalize("https://s.com/a#frag"))
                .isEqualTo("https://s.com/a");
    }

    @Test
    void nullOrBlank_returnsNull() {
        assertThat(LinkHash.of(null)).isNull();
        assertThat(LinkHash.of("  ")).isNull();
    }

    @Test
    void hashIs40HexLowercase() {
        String h = LinkHash.of("https://s.com/x");
        assertThat(h).hasSize(40).matches("[0-9a-f]{40}");
    }
}
