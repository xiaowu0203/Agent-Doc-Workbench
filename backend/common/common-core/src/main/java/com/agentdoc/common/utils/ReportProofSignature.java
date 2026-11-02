package com.agentdoc.common.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/** 离线实验首次报告的短期候选证明请求签名。 */
public final class ReportProofSignature {
    public static final String HEADER = "X-Report-Proof";
    private static final long TTL_SECONDS = 60;

    private ReportProofSignature() { }

    public static String sign(String key, Long id, Long spaceId, String hash, Instant now) {
        long timestamp = now.getEpochSecond();
        return timestamp + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac(key, id, spaceId, hash, timestamp));
    }

    public static boolean verify(String key, Long id, Long spaceId, String hash, String token, Instant now) {
        try {
            if (token == null || token.length() > 100) return false;
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) return false;
            long timestamp = Long.parseLong(parts[0]);
            long age = now.getEpochSecond() - timestamp;
            if (age < 0 || age > TTL_SECONDS) return false;
            return MessageDigest.isEqual(mac(key, id, spaceId, hash, timestamp),
                    Base64.getUrlDecoder().decode(parts[1]));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] mac(String key, Long id, Long spaceId, String hash, long timestamp) {
        if (key == null || id == null || id <= 0 || spaceId == null || spaceId <= 0
                || hash == null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("无效的报告证明参数");
        byte[] bytes = Base64.getDecoder().decode(key);
        if (bytes.length < 32) throw new IllegalArgumentException("报告证明密钥至少需要 32 字节");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(bytes, "HmacSHA256"));
            return mac.doFinal(("report-proof-v1\n" + id + "\n" + spaceId + "\n" + hash + "\n" + timestamp)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法计算报告证明", exception);
        }
    }
}
