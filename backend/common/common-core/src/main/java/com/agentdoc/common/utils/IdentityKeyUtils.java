package com.agentdoc.common.utils;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 外部身份摘要协议v1；不归一化issuer或subject。
 * <p>
 * 用于把OIDC外部IdP的 issuer + subject 计算成稳定唯一的二进制摘要，
 * 作为外部身份的关联键，用来映射外部身份到平台内部userId；
 * 注意：不做URL归一、大小写归一等标准化，输入原样编码参与哈希，上下游必须保证传入一致。
 * </p>
 */
public final class IdentityKeyUtils {

    // 协议版本前缀，用于区分不同版本摘要算法，防止跨版本碰撞
    private static final byte[] PREFIX = "adw-identity-v1".getBytes(StandardCharsets.US_ASCII);

    private IdentityKeyUtils() { }

    /**
     * 按长度前缀编码UTF-8并返回SHA-256；非法Unicode或空值不接受。
     * 编码结构：prefix + [iss长度(4字节大端)][iss utf8] + [sub长度(4字节大端)][sub utf8]
     * @param issuer OIDC issuer（身份提供商地址）
     * @param subject OIDC sub（外部用户唯一标识）
     * @return SHA-256摘要字节数组，作为外部身份主键
     * @throws IllegalArgumentException issuer/sub为空、包含无法编码的非法字符
     * @throws IllegalStateException SHA-256算法缺失
     */
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

    /**
     * 长度前缀编码并写入摘要：先写入4字节大端长度，再写入UTF-8字节；
     * 遇到无法映射、畸形Unicode直接抛出异常，避免自动替换字符导致哈希不一致。
     */
    private static void update(MessageDigest digest, String value) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.remaining()).array());
            digest.update(encoded);
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("身份字段包含非法Unicode", error);
        }
    }
}

