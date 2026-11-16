package com.agentdoc.auth.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** 认证计数和审计的固定身份哈希协议；不处理密码或可还原Token。 */
public final class AuthenticationPrivacyUtils {
    private AuthenticationPrivacyUtils() { }
    public static String normalizeUsername(String username) { return username.strip().toLowerCase(Locale.ROOT); }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256不可用"); }
    }
}
