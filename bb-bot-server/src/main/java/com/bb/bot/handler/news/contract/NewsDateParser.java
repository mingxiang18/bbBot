package com.bb.bot.handler.news.contract;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 把 RSS 原始 pubDate 字符串解析为本地 {@link LocalDateTime}（Phase 2：用于时间窗候选）。
 *
 * <p>覆盖中英文 RSS 常见格式：RFC-822/1123（{@code Mon, 30 May 2026 08:00:00 GMT/+0800}）、
 * ISO-8601 带偏移（{@code 2026-06-03T08:12:00+08:00 / ...Z}）、无偏移的
 * {@code yyyy-MM-dd['T'/' ']HH:mm:ss} 以及纯日期。带时区/偏移者统一折算到系统默认时区。</p>
 *
 * <p>鲁棒性：① 先剥掉 RFC822 的星期前缀（{@code "Mon, "}）——很多源给的星期与日期不一致，
 * 严格解析会拒绝；剥掉后用不校验星期的格式解析。② 解析不出时返回 {@code null}。</p>
 */
public final class NewsDateParser {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** RFC822 星期前缀，如 "Mon, " / "Tue,"。 */
    private static final java.util.regex.Pattern DOW_PREFIX =
            java.util.regex.Pattern.compile("^[A-Za-z]{3,9},\\s*");

    /** 带时区/偏移的格式（已剥星期），统一折算到本地。 */
    private static final List<DateTimeFormatter> ZONED = List.of(
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME
    );

    /** 无时区的本地格式。 */
    private static final List<DateTimeFormatter> LOCAL = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );

    private static final List<DateTimeFormatter> DATE_ONLY = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private NewsDateParser() {
    }

    /**
     * @param raw RSS pubDate 原始字符串，可空
     * @return 折算到系统时区的 LocalDateTime；无法解析返回 {@code null}
     */
    public static LocalDateTime parse(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String s = raw.trim();
        // 剥掉 RFC822 星期前缀，避免星期与日期不一致导致严格解析失败
        String noDow = DOW_PREFIX.matcher(s).replaceFirst("");

        for (DateTimeFormatter f : ZONED) {
            // ZonedDateTime.parse 同吃数字偏移(+0800/+08:00/Z)与区名(GMT)，比 OffsetDateTime 宽容
            try {
                return ZonedDateTime.parse(noDow, f).withZoneSameInstant(ZONE).toLocalDateTime();
            } catch (Exception ignore) {
                // next formatter
            }
        }
        for (DateTimeFormatter f : LOCAL) {
            try {
                return LocalDateTime.parse(s, f);
            } catch (Exception ignore) {
                // next
            }
        }
        for (DateTimeFormatter f : DATE_ONLY) {
            try {
                return LocalDate.parse(s, f).atStartOfDay();
            } catch (Exception ignore) {
                // next
            }
        }
        return null;
    }
}
