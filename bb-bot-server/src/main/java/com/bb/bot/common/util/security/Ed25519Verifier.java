package com.bb.bot.common.util.security;

import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public class Ed25519Verifier {

    public static boolean verify(String secret, String timestamp, byte[] httpBody, String signatureHex) {
        String seed = prepareSeed(secret);

        Ed25519KeyPairGenerator generator = new Ed25519KeyPairGenerator();
        generator.init(new KeyGenerationParameters(null, 32));
        // 使用种子初始化私钥参数
        Ed25519PrivateKeyParameters privateKeyParams = new Ed25519PrivateKeyParameters(seed.getBytes(StandardCharsets.UTF_8), 0);
        // 从私钥参数中提取公钥参数
        Ed25519PublicKeyParameters publicKeyParams = privateKeyParams.generatePublicKey();
        // 将参数转换为字节数组
        byte[] privateKeyBytes = privateKeyParams.getEncoded();
        byte[] publicKeyBytes = publicKeyParams.getEncoded();

        try {
            // 解码签名
            byte[] sig = HexFormat.of().parseHex(signatureHex);
            // 检查签名长度和格式
            if (sig.length != 64 || (sig[63] & 0xE0) != 0) {
                return false;
            }
            // 组成签名体
            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            msg.write(timestamp.getBytes(StandardCharsets.UTF_8));
            msg.write(httpBody);
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(publicKeyBytes, 0));
            verifier.update(msg.toByteArray(), 0, msg.size());
            return verifier.verifySignature(sig);
        } catch (Exception e) {
            return false;
        }
    }

    private static String prepareSeed(String seed) {
        if (seed.length() < 32) seed = seed.repeat(2);
        return seed.substring(0, 32);
    }
}
