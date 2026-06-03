package com.bb.bot.handler.news.contract;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 链接去重键的统一计算口径（共享契约的一部分）。
 *
 * <p>采集（T2）与持久化去重/回写（T1）必须用同一份归一化 + 哈希逻辑，否则
 * {@code linkHash} 不一致会导致 AI 字段回写匹配落空。本类是唯一实现，二者复用。</p>
 *
 * <p>口径(Phase 4): 把 URL 归一化为 {@code scheme://authority + path}, 丢弃 fragment,
 * query 只剔除追踪参(utm_ 前缀 / spm / from / ref / source / fbclid / gclid 等)而保留业务参(id/articleId/p 等),
 * 避免误删以 query 作为文章 ID 的源; 保留参按 key 排序以稳定哈希. 再做 SHA-1, 取 40 位小写 hex.</p>
 */
public final class LinkHash {

    /** 追踪参精确名（小写）。 */
    private static final Set<String> TRACKING_KEYS = Set.of(
            "spm", "scm", "from", "ref", "ref_src", "source", "src",
            "fbclid", "gclid", "yclid", "msclkid", "mc_cid", "mc_eid",
            "_hsenc", "_hsmi", "vt", "share_token");

    private LinkHash() {
    }

    /**
     * 计算链接的去重键。
     *
     * @param link 原始链接
     * @return 40 位小写 hex 的 SHA-1；link 为空时返回 null
     */
    public static String of(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        return sha1Hex(normalize(link));
    }

    /** 归一化：scheme://authority + path + 过滤后的 query，去掉 fragment。URI 解析失败时字符串兜底。 */
    static String normalize(String link) {
        String trimmed = link.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            String path = uri.getPath();
            if (scheme != null && authority != null) {
                String kept = filterQuery(uri.getRawQuery());
                return scheme + "://" + authority + (path == null ? "" : path)
                        + (kept.isEmpty() ? "" : "?" + kept);
            }
        } catch (Exception ignored) {
            // 落字符串兜底
        }
        // 字符串兜底：去 fragment，再过滤 query
        int h = trimmed.indexOf('#');
        if (h >= 0) {
            trimmed = trimmed.substring(0, h);
        }
        int q = trimmed.indexOf('?');
        if (q >= 0) {
            String base = trimmed.substring(0, q);
            String kept = filterQuery(trimmed.substring(q + 1));
            return kept.isEmpty() ? base : base + "?" + kept;
        }
        return trimmed;
    }

    /** 过滤 query：剔除追踪参，保留业务参，并按 key 排序以稳定哈希。 */
    static String filterQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = (eq >= 0 ? pair.substring(0, eq) : pair).toLowerCase();
            if (key.startsWith("utm_") || TRACKING_KEYS.contains(key)) {
                continue;
            }
            kept.add(pair);
        }
        kept.sort(String::compareTo);
        return String.join("&", kept);
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(40);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 在标准 JDK 必然存在
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
