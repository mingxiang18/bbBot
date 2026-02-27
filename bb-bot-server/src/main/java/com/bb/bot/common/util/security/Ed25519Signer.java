package com.bb.bot.common.util.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;

public class Ed25519Signer {

    public static String generateSignature(String botSecret,
                                           String plainToken,
                                           String eventTs) throws Exception {

        // 1 构造 32 字节 seed
        String seed = botSecret;
        while (seed.length() < 32) {
            seed += seed;
        }
        seed = seed.substring(0, 32);

        byte[] seedBytes = seed.getBytes(StandardCharsets.UTF_8);

        // 2 构造 PKCS8 私钥
        byte[] privateKeyBytes = buildPrivateKeyFromSeed(seedBytes);

        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
        PrivateKey privateKey =
                keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));

        // 3 拼接签名内容
        String message = eventTs + plainToken;

        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));

        byte[] signed = signature.sign();

        return bytesToHex(signed);
    }

    // ASN.1 前缀 + seed
    private static byte[] buildPrivateKeyFromSeed(byte[] seed) {
        byte[] prefix = new byte[]{
                0x30, 0x2e,
                0x02, 0x01, 0x00,
                0x30, 0x05,
                0x06, 0x03, 0x2b, 0x65, 0x70,
                0x04, 0x22,
                0x04, 0x20
        };

        byte[] result = new byte[prefix.length + seed.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(seed, 0, result, prefix.length, seed.length);
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}