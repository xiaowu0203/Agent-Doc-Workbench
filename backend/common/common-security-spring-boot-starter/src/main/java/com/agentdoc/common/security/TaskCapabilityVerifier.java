package com.agentdoc.common.security;

import com.agentdoc.common.constant.JwtConstant;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Agent任务能力令牌验签器
 * <p>
 * 复用系统全局{@link JwtDecoder}完成JWT基础校验：签名合法性、过期(exp)、生效时间(nbf)；
 * 在此之上增加业务语义校验，确认该JWT是auth‑service签发的Agent任务能力令牌，拒绝普通用户Access‑Token或其他类型JWT。
 * </p>
 * <p>
 * 校验规则：
 * <ul>
 * <li>1. JwtDecoder.decode：校验签名未被篡改、令牌未过期、已到生效时间；签名/时间异常直接抛出{@link org.springframework.security.oauth2.jwt.JwtException}</li>
 * <li>2. 业务claim校验：{@link JwtConstant#CLAIM_ACTOR_TYPE}必须等于{@link JwtConstant#ACTOR_AGENT}</li>
 * <li>3. 业务claim校验：{@link JwtConstant#CLAIM_SCOPE}必须等于{@link JwtConstant#SCOPE_AGENT}</li>
 * </ul>
 * </p>
 * <p>
 * 抛出异常说明：
 * <ul>
 * <li>{@link org.springframework.security.oauth2.jwt.JwtException}：签名错误、令牌过期、格式非法等基础JWT异常</li>
 * <li>{@link IllegalStateException}：JWT格式合法，但claim不匹配，不是Agent任务能力令牌</li>
 * </ul>
 * </p>
 * <p>
 * 注意：本类只做令牌本身合法性校验；<b>不做业务权限校验</b>（spaceId、documentId、action、任务状态等业务鉴权由业务层{@code requireAgentCapability}完成）。
 * </p>
 */
public class TaskCapabilityVerifier {

    /** JWT解码器，从JWKS拉取公钥，完成签名、时间校验 */
    private final JwtDecoder decoder;

    public TaskCapabilityVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * 校验Agent任务能力JWT令牌
     * @param token X‑TASK‑CAPABILITY 请求头携带的JWT令牌字符串
     * @return 解析完成的Jwt对象，可从中读取各类claim载荷
     * @throws org.springframework.security.oauth2.jwt.JwtException 签名篡改、过期、格式错误
     * @throws IllegalStateException claim不满足Agent令牌业务定义，不是有效的任务能力JWT
     */
    public Jwt verify(String token) {
        // 底层解码器完成签名、exp、nbf校验，非法直接抛出JwtException
        Jwt jwt = decoder.decode(token);
        // 业务声明校验：限定主体类型为Agent，作用域为Agent任务令牌
        if (!JwtConstant.ACTOR_AGENT.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE))
                || !JwtConstant.SCOPE_AGENT.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SCOPE))) {
            throw new IllegalStateException("不是有效的任务能力 JWT");
        }
        return jwt;
    }
}
