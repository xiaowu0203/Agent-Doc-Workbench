package com.agentdoc.common.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * 通用 AES‑GCM 加解密工具
 * <p>
 * 算法：AES‑GCM‑NoPadding，具备加密 + GCM标签完整性校验，可检测密文篡改；
 * 密文格式：{@code v1:Base64(IV(12字节) + 密文+GCM认证标签)}；
 * IV每次加密随机生成，保存在密文内部，无需独立存储；
 * 本类无Spring依赖，可在多个微服务复用；仅抛出运行时异常，由上层业务Service包装业务错误信息。
 * </p>
 * <p>
 * 注意：密钥校验延迟到实际加解密时执行，构造器仅保存密钥字符串，不做解析校验。
 * </p>
 */
public final class AesGcmCrypto {

    /** 密文版本前缀，用于区分加密算法版本，为后续平滑升级预留 */
    private static final String VERSION = "v1:";
    /** AES‑GCM 推荐IV长度：12字节（NIST标准推荐） */
    private static final int IV_LENGTH = 12;
    /** GCM认证标签长度，单位bit，用于完整性校验、防篡改 */
    private static final int TAG_LENGTH = 128;
    /** AES合法密钥字节长度：16(AES‑128)、24(AES‑192)、32(AES‑256) */
    private static final Set<Integer> VALID_KEY_LENGTHS = Set.of(16, 24, 32);
    /** 安全随机数生成器，用于生成每次加密独立的IV */
    private final SecureRandom secureRandom = new SecureRandom();
    /** Base64编码的AES原始密钥字符串，延迟解析，加解密时才做decode与校验 */
    private final String configuredKey;

    /**
     * 创建 AES‑GCM 组件。
     * <p>构造仅保存密钥字符串，<b>不做解析与合法性校验</b>；校验延迟到 encrypt / decrypt 调用时执行。</p>
     *
     * @param configuredKey Base64 编码的 AES 密钥；实际加解密时校验密钥格式与长度
     */
    public AesGcmCrypto(String configuredKey) {
        this.configuredKey = configuredKey;
    }

    /**
     * 加密明文；null 或空白输入返回 null。
     *
     * @param plaintext 待加密明文，可以为null
     * @return 带版本前缀的 Base64 密文；输入null/空白返回null
     * @throws IllegalStateException 密钥未配置、密钥非法、加密运算异常
     */
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
            // payload = IV + (密文+GCM标签)
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("AES-GCM 加密失败", exception);
        }
    }

    /**
     * 解密密文；null 或空白输入返回 null。
     * <p>GCM标签校验失败会抛出异常，代表密文被篡改或密钥错误。</p>
     *
     * @param ciphertext {@link #encrypt(String)} 生成的带版本前缀密文
     * @return 原始明文；输入null/空白返回null
     * @throws IllegalArgumentException 密文版本不匹配、密文字节格式非法
     * @throws IllegalStateException 密钥未配置、密钥非法、解密失败、密文被篡改
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        if (!ciphertext.startsWith(VERSION)) {
            throw new IllegalArgumentException("不支持的 AES-GCM 密文版本");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(VERSION.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("AES-GCM 密文格式无效");
            }
            // 从payload头部拆分IV和加密体
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("AES-GCM 解密失败", exception);
        }
    }

    /**
     * 解析并校验配置密钥，生成AES密钥对象。
     * <p>延迟加载：仅在真正执行加解密时才decode、校验密钥字节。</p>
     *
     * @return AES SecretKeySpec
     * @throws IllegalStateException 密钥为空、base64解码失败、密钥字节长度不合法
     */
    private SecretKeySpec key() {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("未配置 AES-GCM 密钥");
        }
        byte[] key = Base64.getDecoder().decode(configuredKey);
        if (!VALID_KEY_LENGTHS.contains(key.length)) {
            throw new IllegalStateException("AES-GCM 密钥必须是 Base64 编码的 16/24/32 字节 AES 密钥");
        }
        return new SecretKeySpec(key, "AES");
    }
}
