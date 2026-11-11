package com.agentdoc.common.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.vo.IdentitySelfVO;
import com.agentdoc.common.utils.OidcHumanTokenUtils;
import com.agentdoc.common.utils.LocalIdentityTokenUtils;
import com.agentdoc.common.enums.AuthenticationMode;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Objects;

/**
 * 仅以已验签人类Bearer查询本人身份；业务授权仍由当前业务服务执行。
 * <p>
 * 两模式共用的JWT转换处理器：
 * 当前模式JWT已经通过专用JwtDecoder验签，本Converter拿着原Token远程调用身份服务，
 * 转换成系统内部 {@link ResolvedHumanAuthentication} 人类身份凭证；
 * 只做身份映射，不做业务权限判定，业务授权交给服务内注解/拦截器处理。
 * </p>
 */
public final class RemoteHumanAuthenticationConverter implements Converter<Jwt, ResolvedHumanAuthentication> {

    private final AuthIdentityFeign identity;
    private final AuthenticationMode mode;

    public RemoteHumanAuthenticationConverter(AuthIdentityFeign identity, AuthenticationMode mode) {
        this.identity = identity;
        this.mode = Objects.requireNonNull(mode, "认证模式不能为空");
    }

    /**
     * 转换逻辑：
     * 1. 先校验令牌声明，确保是人类访问令牌，拒绝非人类能力令牌；
     * 2. 使用同一原始已验签Bearer查询平台内用户信息；
     * 3. 根据返回结果区分异常类型：凭证错误 / 身份服务依赖不可用；
     * 4. 组装内部身份对象，携带userId与平台角色，写入Security上下文。
     */
    @Override
    public ResolvedHumanAuthentication convert(Jwt jwt) {
        // 两模式分别检查人类用途，拒绝能力类令牌进入身份RPC。
        if (mode == AuthenticationMode.OIDC && !OidcHumanTokenUtils.isHumanAccess(jwt)) {
            throw new BadCredentialsException("无效的人类令牌");
        }
        if (mode == AuthenticationMode.LOCAL) {
            try { LocalIdentityTokenUtils.requireHuman(jwt, Instant.now()); }
            catch (JwtException | IllegalArgumentException error) { throw new BadCredentialsException("无效的人类令牌"); }
        }
        try {
            // 不重签、不替换主体，原样查询该Bearer持有者的当前身份。
            Result<IdentitySelfVO> result = identity.currentIdentity("Bearer " + jwt.getTokenValue());
            // 身份服务侧服务级异常（50xxx错误码）→ 标记依赖不可用
            if (result == null || result.code() >= 50000) {
                throw new AuthenticationServiceException("身份服务不可用");
            }
            // 业务错误码：token有效，但用户不存在/禁用等，属于凭证身份无效
            if (result.code() != 0) {
                throw new BadCredentialsException("用户身份不可用");
            }
            IdentitySelfVO resolved = result.data();
            // 返回数据校验，防止返回空或非法userId
            if (resolved == null || resolved.userId() == null || resolved.userId() <= 0
                    || resolved.platformRoles() == null || resolved.authVersion() == null || resolved.authVersion() < 0) {
                throw new AuthenticationServiceException("身份服务返回无效结果");
            }
            if (mode == AuthenticationMode.LOCAL) {
                if (!resolved.userId().toString().equals(jwt.getSubject())) { throw new AuthenticationServiceException("身份服务返回无效结果"); }
                if (!resolved.authVersion().equals(LocalIdentityTokenUtils.authenticationVersion(jwt))) {
                    throw new BadCredentialsException("用户身份不可用");
                }
            }
            // 构建系统内部认证对象，注入Security上下文
            return new ResolvedHumanAuthentication(jwt, resolved.userId(), resolved.platformRoles());
        } catch (AuthenticationException error) {
            // Spring Security标准认证异常直接上抛，由EntryPoint处理
            throw error;
        } catch (RuntimeException error) {
            // Feign网络、超时等运行时异常统一包装为依赖不可用，
            // 不向外透出底层调用栈、请求、令牌等敏感信息
            throw new AuthenticationServiceException("身份服务不可用");
        }
    }
}
