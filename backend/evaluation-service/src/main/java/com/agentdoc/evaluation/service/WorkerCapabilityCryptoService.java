package com.agentdoc.evaluation.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AesGcmCrypto;
import com.agentdoc.evaluation.config.EvaluationProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * WorkerCapability 的服务专属 AES-GCM 加密与密钥轮换读取。
 * 负责对WorkerCapability能力字符串做AES-GCM加密；支持双密钥版本（当前+历史旧密钥）实现密钥平滑轮换解密。
 */
@Service
public class WorkerCapabilityCryptoService {

    private final EvaluationProperties properties;

    public WorkerCapabilityCryptoService(EvaluationProperties properties) {
        this.properties = properties;
    }

    /**
     * 使用当前生效密钥加密capability，返回【密钥版本号 + 密文】封装对象
     * @param capability 原始能力明文串
     * @return EncryptedCapability 携带版本标识的密文
     */
    public EncryptedCapability encrypt(String capability) {
        if (StringUtils.isBlank(properties.capabilityKeyVersion())
                || StringUtils.isBlank(properties.capabilityKey())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Evaluation WorkerCapability 加密密钥未配置");
        }
        return new EncryptedCapability(
                properties.capabilityKeyVersion(),
                new AesGcmCrypto(properties.capabilityKey()).encrypt(capability)
        );
    }

    /**
     * 根据密钥版本选择对应密钥解密，支持当前密钥 / 上一版旧密钥（密钥轮换场景）
     * @param keyVersion 密文附带的密钥版本标识
     * @param encryptedCapability 密文
     * @return capability明文
     */
    public String decrypt(String keyVersion, String encryptedCapability) {
        String key = null;
        // 当前活跃密钥
        if (properties.capabilityKeyVersion() != null
                && properties.capabilityKeyVersion().equals(keyVersion)) {
            key = properties.capabilityKey();
        }
        // 历史旧密钥，用于解密轮换前的存量密文
        else if (properties.previousCapabilityKeyVersion() != null
                && properties.previousCapabilityKeyVersion().equals(keyVersion)) {
            key = properties.previousCapabilityKey();
        }

        if (StringUtils.isBlank(key)) {
            throw new BusinessException(ErrorCode.CONFLICT, "WorkerCapability 密钥版本不可用");
        }
        try {
            return new AesGcmCrypto(key).decrypt(encryptedCapability);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "WorkerCapability 密文无法解密");
        }
    }

    /**
     * 加密结果记录：绑定密钥版本，解密时依靠版本找到对应密钥，支持密钥轮换
     * @param keyVersion 使用的密钥版本标识
     * @param ciphertext AES-GCM密文
     */
    public record EncryptedCapability(String keyVersion, String ciphertext) { }
}
