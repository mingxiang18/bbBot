package com.bb.bot.common.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashUtilTest {

    @Test
    void sha256_emptyVector() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HashUtil.sha256Hex(new byte[0]));
    }

    @Test
    void sha256_abcVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HashUtil.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void sha256_is64LowerHex_andStable() {
        byte[] b = "hello".getBytes(StandardCharsets.UTF_8);
        String h = HashUtil.sha256Hex(b);
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
        assertEquals(h, HashUtil.sha256Hex(b), "同字节内容哈希必须稳定一致");
    }
}
