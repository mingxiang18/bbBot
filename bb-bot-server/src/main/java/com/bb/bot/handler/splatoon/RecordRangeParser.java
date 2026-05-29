package com.bb.bot.handler.splatoon;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析「打工记录2-4」「对战记录」一类指令中的查询区间。
 *
 * <p>抽取自重构前 {@code BbSplatoonUserHandler#getCoopRecords} / {@code getBattleRecords} 两份
 * 等价的内联正则解析逻辑，消除重复。本类只负责纯文本解析与区间校验，不触碰回复/查询/渲染，
 * 因此可被 Handler（T5.2）与 SplatoonRecordTool（T5.3）共用。
 *
 * <p>解析口径（与重构前逐一等价）：
 * <ul>
 *   <li>正则 {@code ^/?<关键字>(\d*)-?(\d*)}，匹配失败 → {@link Result#formatError(RecordType)}；</li>
 *   <li>第一组为空 → 用 {@code defaultStart}；第二组为空 → 用 {@code defaultEnd}；</li>
 *   <li>{@code end - start + 1 > maxSpan} → {@link Result#spanExceeded(int)}。</li>
 * </ul>
 *
 * <p>本类在等价基础上新增的「上限校验」逻辑（重构前内联代码缺失，T5.1 引入但 T5.1 不改调用方）：
 * <ul>
 *   <li>越界裁剪：解析出的 start/end 先夹到 {@code [min, max]} 闭区间；</li>
 *   <li>start &gt; end：裁剪后若起点大于终点，视为格式错误。</li>
 * </ul>
 * 跨度上限判定基于裁剪后的区间，保证 limit 计算结果落在合法范围内。
 */
public final class RecordRangeParser {

    private RecordRangeParser() {
    }

    /**
     * 解析失败的原因分类。
     */
    public enum Error {
        /** 整体格式不匹配，或裁剪后 start &gt; end。回复 {@link RecordType#getFormatErrorHint()}。 */
        FORMAT,
        /** 区间跨度超过上限。回复「查询记录超过N条了，太多啦」。 */
        SPAN_EXCEEDED
    }

    /**
     * 解析结果：要么是合法区间（{@link #isValid()} 为 true，可取 {@link #getStart()}/{@link #getEnd()}），
     * 要么是失败（携带 {@link #getError()}）。不可变值对象。
     */
    public static final class Result {
        private final boolean valid;
        private final int start;
        private final int end;
        private final Error error;
        /** SPAN_EXCEEDED 时携带的上限值（用于拼提示文案）；其余情况为 0。 */
        private final int maxSpan;

        private Result(boolean valid, int start, int end, Error error, int maxSpan) {
            this.valid = valid;
            this.start = start;
            this.end = end;
            this.error = error;
            this.maxSpan = maxSpan;
        }

        static Result ok(int start, int end) {
            return new Result(true, start, end, null, 0);
        }

        static Result formatError() {
            return new Result(false, 0, 0, Error.FORMAT, 0);
        }

        static Result spanExceeded(int maxSpan) {
            return new Result(false, 0, 0, Error.SPAN_EXCEEDED, maxSpan);
        }

        public boolean isValid() {
            return valid;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public Error getError() {
            return error;
        }

        public int getMaxSpan() {
            return maxSpan;
        }

        /** 条数 = end - start + 1（仅在合法区间下有意义）。 */
        public int count() {
            return end - start + 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Result)) {
                return false;
            }
            Result other = (Result) o;
            return valid == other.valid
                    && start == other.start
                    && end == other.end
                    && error == other.error
                    && maxSpan == other.maxSpan;
        }

        @Override
        public int hashCode() {
            return Objects.hash(valid, start, end, error, maxSpan);
        }

        @Override
        public String toString() {
            return valid
                    ? "Result{valid, start=" + start + ", end=" + end + "}"
                    : "Result{error=" + error + (error == Error.SPAN_EXCEEDED ? ", maxSpan=" + maxSpan : "") + "}";
        }
    }

    /** 关键字 -> 预编译正则缓存，避免每条消息重新 compile。 */
    private static final java.util.Map<String, Pattern> PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static Pattern patternFor(RecordType type) {
        return PATTERN_CACHE.computeIfAbsent(type.getKeyword(),
                kw -> Pattern.compile("^/?" + Pattern.quote(kw) + "(\\d*)-?(\\d*)"));
    }

    /**
     * 解析指令文本中的区间。
     *
     * @param message      原始指令文本，如「打工记录2-4」「/对战记录」
     * @param type         记录类型（决定关键字与提示文案）
     * @param defaultStart 未显式给出起点时的默认起点（重构前为 1）
     * @param defaultEnd   未显式给出终点时的默认终点（重构前为 5）
     * @param min          合法起点下限（越界裁剪用，闭区间）
     * @param max          合法终点上限（越界裁剪用，闭区间）
     * @param maxSpan      区间跨度上限（条数上限，重构前为 20）
     * @return 解析结果
     */
    public static Result parse(String message, RecordType type,
                               int defaultStart, int defaultEnd,
                               int min, int max, int maxSpan) {
        if (message == null) {
            return Result.formatError();
        }
        Matcher matcher = patternFor(type).matcher(message);
        if (!matcher.matches()) {
            return Result.formatError();
        }
        int start = matcher.group(1).isEmpty() ? defaultStart : Integer.parseInt(matcher.group(1));
        int end = matcher.group(2).isEmpty() ? defaultEnd : Integer.parseInt(matcher.group(2));

        // 越界裁剪：夹到 [min, max] 闭区间
        start = clamp(start, min, max);
        end = clamp(end, min, max);

        // 裁剪后起点大于终点 → 视为格式错误
        if (start > end) {
            return Result.formatError();
        }
        // 跨度上限判定（基于裁剪后的区间）
        if (end - start + 1 > maxSpan) {
            return Result.spanExceeded(maxSpan);
        }
        return Result.ok(start, end);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
