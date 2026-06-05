package com.bb.bot.common.util;

import java.security.MessageDigest;

/**
 * 全系统统一的内容哈希工具。图片去重 / 视觉描述缓存都用 sha256(bytes) 的 hex，
 * 保证桥、入站规范化、analyze_image、DB 缓存口径一致。
 */
public final class HashUtil {

    private HashUtil() {
    }

    /** 字节内容 sha256，返回小写 hex。 */
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 一定存在
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
