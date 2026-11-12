package com.xiye.garden.auth.service;

import com.xiye.garden.auth.entity.VerificationPurpose;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VerificationProtector {
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public VerificationProtector(@Value("${AUTH_CODE_HMAC_KEY}") String key) {
        try {
            secret = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AUTH_CODE_HMAC_KEY 必须为 Base64 编码的随机密钥");
        }
        if (secret.length < 32) throw new IllegalStateException("验证码密钥至少需要32字节");
    }

    public String generate() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    public String codeHash(String email, VerificationPurpose purpose, String code) {
        return digest("code\n" + email + "\n" + purpose + "\n" + code);
    }

    public String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法初始化验证码保护", exception);
        }
    }

    public boolean matches(String expected, String supplied) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    }
}
