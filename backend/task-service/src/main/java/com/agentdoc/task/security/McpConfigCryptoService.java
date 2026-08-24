package com.agentdoc.task.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Agent MCP 配置的 AES-GCM 加解密服务。
 * <p>密钥只从环境配置读取，数据库仅保存 v1: 前缀和密文，不保存明文凭证。</p>
 */
@Service
public class McpConfigCryptoService {

    private static final String VERSION = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final Set<Integer> VALID_KEY_LENGTHS = Set.of(16, 24, 32);
    private static final String VALID_KEY_LENGTH_DESCRIPTION = "16/24/32";

    private final SecureRandom secureRandom = new SecureRandom();
    private final String configuredKey;

    public McpConfigCryptoService(
            @Value("${agent-doc.security.mcp-config-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("MCP 配置加密失败", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        if (!ciphertext.startsWith(VERSION)) {
            throw new IllegalArgumentException("不支持的 MCP 配置密文版本");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(VERSION.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("MCP 配置密文格式无效");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("MCP 配置解密失败", e);
        }
    }

    private SecretKeySpec key() {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("未配置 agent-doc.security.mcp-config-key");
        }
        byte[] key = Base64.getDecoder().decode(configuredKey);
        if (!VALID_KEY_LENGTHS.contains(key.length)) {
            throw new IllegalStateException("MCP 配置密钥必须是 Base64 编码的 "
                    + VALID_KEY_LENGTH_DESCRIPTION + " 字节 AES 密钥");
        }
        return new SecretKeySpec(key, "AES");
    }
}
