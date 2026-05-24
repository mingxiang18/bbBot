package com.bb.bot.aiAgent.memory;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.List;

/**
 * 对应 openhanako 的 fingerprint 机制（compile.js#computeFingerprint）：
 * MD5 of concatenated keys。输入 keys 没变 + 目标文件存在 → skip 重编译。
 */
@Slf4j
public final class MemoryFingerprint {

    private MemoryFingerprint() {}

    public static String compute(List<String> keys) {
        if (keys == null || keys.isEmpty()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) md.update((byte) '\n');
                md.update(keys.get(i).getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** target 是被编译的文件（.md），fingerprint 文件是 target.fingerprint */
    public static boolean isStillFresh(Path target, String currentFingerprint) {
        Path fp = target.resolveSibling(target.getFileName().toString() + ".fingerprint");
        if (!Files.exists(target) || !Files.exists(fp)) return false;
        try {
            String saved = Files.readString(fp, StandardCharsets.UTF_8).trim();
            return saved.equals(currentFingerprint);
        } catch (IOException e) {
            return false;
        }
    }

    public static void save(Path target, String fingerprint) {
        Path fp = target.resolveSibling(target.getFileName().toString() + ".fingerprint");
        try {
            Files.createDirectories(fp.getParent());
            Files.writeString(fp, fingerprint, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.warn("写 fingerprint 失败 {}", fp, e);
        }
    }

    public static void invalidate(Path target) {
        Path fp = target.resolveSibling(target.getFileName().toString() + ".fingerprint");
        try {
            Files.deleteIfExists(fp);
        } catch (IOException ignore) {}
    }
}
