package com.bb.bot.handler.news.contract;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 链接去重键的统一计算口径（共享契约的一部分）。
 *
 * <p>采集（T2）与持久化去重/回写（T1）必须用同一份归一化 + 哈希逻辑，否则
 * {@code linkHash} 不一致会导致 AI 字段回写匹配落空。本类是唯一实现，二者复用。</p>
 *
 * <p>口径：把 URL 归一化为 {@code scheme://authority + path}（丢弃 query 与 fragment），
 * 再做 SHA-1，取 40 位小写 hex。</p>
 */
public final class LinkHash {

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

    /** 归一化：scheme://authority + path，去掉 query 与 fragment。URI 解析失败时字符串兜底。 */
    static String normalize(String link) {
        String trimmed = link.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String authority = uri.getAuthority();
            String path = uri.getPath();
            if (scheme != null && authority != null) {
                return scheme + "://" + authority + (path == null ? "" : path);
            }
        } catch (Exception ignored) {
            // 落字符串兜底
        }
        int q = trimmed.indexOf('?');
        if (q >= 0) {
            trimmed = trimmed.substring(0, q);
        }
        int h = trimmed.indexOf('#');
        if (h >= 0) {
            trimmed = trimmed.substring(0, h);
        }
        return trimmed;
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
