package com.agentdoc.agent.security;

import com.agentdoc.common.utils.AesGcmCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent 配置加密解密服务。
 * <p>使用 agent-config-key 保护模型 API Key 及其他 Agent 敏感配置。</p>
 */
@Service
public class AgentConfigCryptoService {

    private final AesGcmCrypto crypto;

    /**
     * 构造注入业务加密密钥配置，初始化通用加密工具实例
     *
     * @param configuredKey 配置项 {@code agent-doc.security.agent-config-key}，Base64编码AES密钥
     */
    public AgentConfigCryptoService(@Value("${agent-doc.security.agent-config-key:}") String configuredKey) {
        this.crypto = new AesGcmCrypto(configuredKey);
    }

    /**
     * 加密明文，输出带版本前缀的Base64密文
     *
     * @param plaintext 待加密明文（模型API‑Key等敏感配置）
     * @return 密文字符串；输入null/空白返回null
     * @throws IllegalStateException 密钥非法、底层加密运算异常时抛出
     */
    public String encrypt(String plaintext) {
        try {
            return crypto.encrypt(plaintext);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Agent 配置加密失败", exception);
        }
    }

    /**
     * 解密密文，还原原始明文
     *
     * @param ciphertext {@link #encrypt}输出的带版本标记密文
     * @return 原始明文；输入null/空白返回null
     * @throws IllegalArgumentException 密文版本不匹配、密文格式非法
     * @throws IllegalStateException 密钥异常、解密校验失败、密文被篡改
     */
    public String decrypt(String ciphertext) {
        try {
            return crypto.decrypt(ciphertext);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 Agent 配置密文版本", exception);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Agent 配置解密失败", exception);
        }
    }
}
