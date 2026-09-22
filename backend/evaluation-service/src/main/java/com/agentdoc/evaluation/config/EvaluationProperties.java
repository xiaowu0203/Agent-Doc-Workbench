package com.agentdoc.evaluation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Evaluation 服务部署参数。
 *
 * @param capabilityKeyVersion 当前能力密钥版本号，用于区分多套密钥，密钥轮换时更新
 * @param capabilityKey 当前版本能力密钥，禁止硬编码，仅由环境变量/密钥管理服务注入
 * @param previousCapabilityKeyVersion 上一版能力密钥版本号，用于密钥平滑轮换，兼容存量签名/凭证
 * @param previousCapabilityKey 上一版能力密钥，密钥切换过渡期使用，用于校验历史凭证
 */
@ConfigurationProperties(prefix = "agent-doc.evaluation")
public record EvaluationProperties(
        String capabilityKeyVersion,
        String capabilityKey,
        String previousCapabilityKeyVersion,
        String previousCapabilityKey) {
}