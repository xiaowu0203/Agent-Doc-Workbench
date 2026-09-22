package com.agentdoc.common.security;

import com.agentdoc.common.constant.JwtConstant;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Evaluation Worker 能力令牌校验器
 * 用于校验JWT令牌是否为合法的Evaluation Worker能力凭证，校验演员类型、作用域、服务标识、受众等声明
 */
public class EvaluationWorkerCapabilityVerifier {
    /**
     * JWT解码器，用于解析token生成Jwt对象
     */
    private final JwtDecoder decoder;

    /**
     * 构造注入JWT解码器
     *
     * @param decoder JWT解码器实例
     */
    public EvaluationWorkerCapabilityVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * 校验Evaluation Worker能力JWT令牌
     * <p>
     * 校验规则：
     * 1. actor_type 必须为 ACTOR_SERVICE 服务类型
     * 2. scope 必须为 SCOPE_SERVICE 服务作用域
     * 3. service 必须为 EVALUATION_SERVICE 评估服务标识
     * 4. audience 受众不能为空，且必须包含 EVALUATION_WORKER_CAPABILITY_AUDIENCE
     * </p>
     *
     * @param token JWT字符串
     * @return 校验通过后的Jwt对象
     * @throws IllegalStateException 令牌声明不满足要求时抛出异常
     */
    public Jwt verify(String token) {
        // 解码token，获取JWT载荷信息
        Jwt jwt = decoder.decode(token);
        // 校验各项声明：类型、作用域、服务标识、受众
        if (!JwtConstant.ACTOR_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE))
                || !JwtConstant.SCOPE_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SCOPE))
                || !JwtConstant.EVALUATION_SERVICE.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SERVICE))
                || jwt.getAudience() == null
                || !jwt.getAudience().contains(JwtConstant.EVALUATION_WORKER_CAPABILITY_AUDIENCE)) {
            throw new IllegalStateException("不是有效的 Evaluation WorkerCapability JWT");
        }
        return jwt;
    }
}
