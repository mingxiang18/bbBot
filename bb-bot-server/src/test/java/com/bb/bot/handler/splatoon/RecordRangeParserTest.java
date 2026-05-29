package com.bb.bot.handler.splatoon;

import com.bb.bot.handler.splatoon.RecordRangeParser.Error;
import com.bb.bot.handler.splatoon.RecordRangeParser.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RecordRangeParser} 全边界单测。
 *
 * <p>沿用重构前 {@code BbSplatoonUserHandler} 的口径：默认区间 1-5、跨度上限 20。
 * min/max 取 1..1000（足够大，等价于重构前几乎不裁剪），越界裁剪用例单独把上限调小验证。
 */
class RecordRangeParserTest {

    private static final int DEFAULT_START = 1;
    private static final int DEFAULT_END = 5;
    private static final int MIN = 1;
    private static final int MAX = 1000;
    private static final int MAX_SPAN = 20;

    private static Result parse(String msg, RecordType type) {
        return RecordRangeParser.parse(msg, type, DEFAULT_START, DEFAULT_END, MIN, MAX, MAX_SPAN);
    }

    // ---------- 无区间：用默认值 ----------

    @Test
    void coopNoRangeUsesDefault() {
        Result r = parse("打工记录", RecordType.COOP);
        assertTrue(r.isValid());
        assertEquals(1, r.getStart());
        assertEquals(5, r.getEnd());
        assertEquals(5, r.count());
    }

    @Test
    void battleNoRangeUsesDefault() {
        Result r = parse("对战记录", RecordType.BATTLE);
        assertTrue(r.isValid());
        assertEquals(1, r.getStart());
        assertEquals(5, r.getEnd());
    }

    @Test
    void leadingSlashAccepted() {
        Result r = parse("/打工记录", RecordType.COOP);
        assertTrue(r.isValid());
        assertEquals(1, r.getStart());
        assertEquals(5, r.getEnd());
    }

    // ---------- 单值 ----------

    @Test
    void singleStartOnly() {
        // 「打工记录3」：起点 3，终点取默认 5
        Result r = parse("打工记录3", RecordType.COOP);
        assertTrue(r.isValid());
        assertEquals(3, r.getStart());
        assertEquals(5, r.getEnd());
    }

    @Test
    void singleStartWithTrailingDash() {
        // 「打工记录3-」：第二组为空 → 终点取默认 5
        Result r = parse("打工记录3-", RecordType.COOP);
        assertTrue(r.isValid());
        assertEquals(3, r.getStart());
        assertEquals(5, r.getEnd());
    }

    // ---------- 区间 ----------

    @Test
    void explicitRange() {
        Result r = parse("打工记录2-4", RecordType.COOP);
        assertTrue(r.isValid());
        assertEquals(2, r.getStart());
        assertEquals(4, r.getEnd());
        assertEquals(3, r.count());
    }

    @Test
    void battleExplicitRange() {
        Result r = parse("对战记录2-11", RecordType.BATTLE);
        assertTrue(r.isValid());
        assertEquals(2, r.getStart());
        assertEquals(11, r.getEnd());
    }

    @Test
    void singleEqualBounds() {
        Result r = parse("打工记录7-7", RecordType.COOP);
        assertTrue(r.isValid());
        assertEquals(7, r.getStart());
        assertEquals(7, r.getEnd());
        assertEquals(1, r.count());
    }

    // ---------- 超上限报错（跨度） ----------

    @Test
    void spanExactlyAtLimitIsOk() {
        // 1-20 = 20 条，正好等于上限，合法
        Result r = parse("打工记录1-20", RecordType.COOP);
        assertTrue(r.isValid());
        assertEquals(20, r.count());
    }

    @Test
    void spanOverLimitFails() {
        // 1-21 = 21 条 > 20
        Result r = parse("打工记录1-21", RecordType.COOP);
        assertFalse(r.isValid());
        assertEquals(Error.SPAN_EXCEEDED, r.getError());
        assertEquals(MAX_SPAN, r.getMaxSpan());
    }

    @Test
    void spanOverLimitFailsBattle() {
        Result r = parse("对战记录3-30", RecordType.BATTLE);
        assertFalse(r.isValid());
        assertEquals(Error.SPAN_EXCEEDED, r.getError());
    }

    // ---------- 格式错误 ----------

    @Test
    void wrongKeywordIsFormatError() {
        // 关键字不匹配（对战 vs 打工）
        Result r = parse("对战记录2-4", RecordType.COOP);
        assertFalse(r.isValid());
        assertEquals(Error.FORMAT, r.getError());
    }

    @Test
    void trailingGarbageIsFormatError() {
        // 正则 ^/?打工记录(\d*)-?(\d*) 为 matches()，尾部多余文字使整体不匹配
        Result r = parse("打工记录abc", RecordType.COOP);
        assertFalse(r.isValid());
        assertEquals(Error.FORMAT, r.getError());
    }

    @Test
    void nullMessageIsFormatError() {
        Result r = parse(null, RecordType.COOP);
        assertFalse(r.isValid());
        assertEquals(Error.FORMAT, r.getError());
    }

    @Test
    void emptyMessageIsFormatError() {
        Result r = parse("", RecordType.COOP);
        assertFalse(r.isValid());
        assertEquals(Error.FORMAT, r.getError());
    }

    // ---------- start > end ----------

    @Test
    void startGreaterThanEndIsFormatError() {
        Result r = parse("打工记录5-2", RecordType.COOP);
        assertFalse(r.isValid());
        assertEquals(Error.FORMAT, r.getError());
    }

    // ---------- 越界裁剪（min/max 调小验证） ----------

    @Test
    void endClampedToMax() {
        // max=10，请求 1-50 → 终点裁剪到 10，条数 10 <= 20
        Result r = RecordRangeParser.parse("打工记录1-50", RecordType.COOP,
                DEFAULT_START, DEFAULT_END, /*min*/1, /*max*/10, MAX_SPAN);
        assertTrue(r.isValid());
        assertEquals(1, r.getStart());
        assertEquals(10, r.getEnd());
    }

    @Test
    void startClampedToMin() {
        // min=3，请求起点 1 → 裁剪到 3
        Result r = RecordRangeParser.parse("打工记录1-8", RecordType.COOP,
                DEFAULT_START, DEFAULT_END, /*min*/3, /*max*/1000, MAX_SPAN);
        assertTrue(r.isValid());
        assertEquals(3, r.getStart());
        assertEquals(8, r.getEnd());
    }

    @Test
    void clampCollapsesToFormatErrorWhenStartExceedsMax() {
        // max=10，起点 50 → 裁剪到 10；终点默认 5 → 裁剪保持 5；start(10) > end(5) → 格式错误
        Result r = RecordRangeParser.parse("打工记录50", RecordType.COOP,
                DEFAULT_START, DEFAULT_END, /*min*/1, /*max*/10, MAX_SPAN);
        assertFalse(r.isValid());
        assertEquals(Error.FORMAT, r.getError());
    }

    @Test
    void clampThenSpanStillEnforced() {
        // max=100，请求 1-100 → 裁剪后仍 100 条 > 20 → 超上限
        Result r = RecordRangeParser.parse("打工记录1-100", RecordType.COOP,
                DEFAULT_START, DEFAULT_END, /*min*/1, /*max*/100, MAX_SPAN);
        assertFalse(r.isValid());
        assertEquals(Error.SPAN_EXCEEDED, r.getError());
    }

    // ---------- Result 值语义 ----------

    @Test
    void resultEqualityAndCount() {
        Result a = parse("打工记录2-4", RecordType.COOP);
        Result b = parse("打工记录2-4", RecordType.COOP);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(3, a.count());
    }
}
