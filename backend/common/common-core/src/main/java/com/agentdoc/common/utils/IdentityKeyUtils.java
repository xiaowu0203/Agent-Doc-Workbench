package com.agentdoc.common.utils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 外部身份摘要协议v1；不归一化issuer或subject。 */
public final class IdentityKeyUtils {
    private static final byte[] PREFIX = "adw-identity-v1".getBytes(StandardCharsets.US_ASCII);

    private IdentityKeyUtils() { }

    /** 按长度前缀编码UTF-8并返回SHA-256；非法Unicode或空值不接受。 */
    public static byte[] identityKey(String issuer, String subject) {
        if (issuer == null || issuer.isEmpty() || subject == null || subject.isEmpty()) {
            throw new IllegalArgumentException("issuer和subject不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PREFIX);
            update(digest, issuer);
            update(digest, subject);
            return digest.digest();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256不可用", error);
        }
    }

    private static void update(MessageDigest digest, String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.remaining()).array());
            digest.update(encoded);
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("身份字段包含非法Unicode", error);
        }
    }
}
