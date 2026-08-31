package com.agentdoc.common.utils;

import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * 认证上下文工具类
 * <p>
 * 从 Spring Security 安全上下文中读取当前已认证的 JWT 信息，
 * 获取登录用户ID、AgentID等业务声明；
 * <b>注意：仅在Web请求主线程有效；异步(@Async/线程池)线程拿不到SecurityContext，需要上层手动传递令牌信息。</b>
 * <p>
 * 前提：OAuth2 ResourceServer 已完成JWT解析并填充 SecurityContext，
 * 匿名、未登录场景全部返回null，不抛出异常。
 */
public final class AuthUtils {

    private AuthUtils() {
    }

    /**
     * 获取当前请求已认证的JWT对象
     * @return 已解析认证通过的JWT；未登录、匿名用户返回null
     */
    public static Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 判断认证对象非空、不是匿名身份、principal为JWT实例
        if (authentication != null
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    /**
     * 获取当前人类用户 ID，取自用户 JWT 的 sub 字段。
     * <p>Agent 任务 Token 的 sub 是 taskId，不会被当作用户 ID 返回。</p>
     * @return 人类用户 ID；未登录、匿名或 Agent 任务 Token 返回null
     */
    public static Long getUserId() {
        Jwt jwt = currentJwt();
        if (!isHumanToken(jwt)) {
            return null;
        }
        return Long.valueOf(jwt.getSubject());
    }

    /**
     * 获取当前人类用户 ID，未登录或当前为 Agent 任务 Token 时抛 {@link ErrorCode#UNAUTHORIZED}。
     * @return 用户ID
     * @throws BusinessException 未登录、匿名或当前为 Agent 任务 Token，{@link ErrorCode#UNAUTHORIZED}
     */
    public static Long getUserIdOrException() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 获取Agent调用场景下的Agent ID，取自JWT自定义claim {@link JwtConstant#CLAIM_AGENT_ID}
     * <p>普通人类用户请求该claim不存在，返回null。</p>
     * @return Agent编号；非Agent调用、未登录时返回null
     */
    public static Long getAgentId() {
        Jwt jwt = currentJwt();
        if (!isAgentToken(jwt) || !jwt.hasClaim(JwtConstant.CLAIM_AGENT_ID)) {
            return null;
        }
        return Long.valueOf(jwt.getClaimAsString(JwtConstant.CLAIM_AGENT_ID));
    }

    /**
     * 获取Agent调用场景下的Agent ID：非Agent/未登录抛 {@link ErrorCode#UNAUTHORIZED}。
     * @return Agent编号
     * @throws BusinessException 非Agent调用/未登录，{@link ErrorCode#UNAUTHORIZED}
     */
    public static Long getAgentIdOrException() {
        Long agentId = getAgentId();
        if (agentId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return agentId;
    }

    /**
     * 获取当前上下文JWT中的任务ID
     * <p>仅当当前令牌为 Task‑Capability 任务短时能力JWT时才有值；普通用户登录JWT返回null</p>
     * @return 任务ID，无令牌或非任务令牌返回null
     */
    public static Long getTaskId() {
        return getTaskClaim(JwtConstant.CLAIM_TASK_ID);
    }

    /**
     * 获取当前上下文JWT中的空间ID
     * <p>仅当当前令牌为 Task‑Capability 任务短时能力JWT时才有值；普通用户登录JWT返回null</p>
     * @return 空间ID，无令牌或非任务令牌返回null
     */
    public static Long getSpaceId() {
        return getTaskClaim(JwtConstant.CLAIM_SPACE_ID);
    }

    /**
     * 获取当前上下文JWT中的文档ID
     * <p>仅当当前令牌为 Task‑Capability 任务短时能力JWT时才有值；普通用户登录JWT返回null</p>
     * @return 文档ID，无令牌或非任务令牌返回null
     */
    public static Long getDocumentId() {
        return getTaskClaim(JwtConstant.CLAIM_DOCUMENT_ID);
    }

    /**
     * 判断当前上下文主体是否为Agent任务（依据JWT中actorType=AGENT）
     * @return true=当前是Task‑Capability任务能力令牌；false=无令牌/普通用户登录JWT
     */
    public static boolean isAgent() {
        Jwt jwt = currentJwt();
        return jwt != null && JwtConstant.ACTOR_AGENT.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE));
    }

    /**
     * 判断当前用户 JWT 是否声明指定平台角色。
     * <p>该方法只用于远程实时确认前的快速判断，最终高权限授权仍需回查 auth-service。</p>
     * @param roleKey 平台角色标识
     * @return true 当前用户 JWT 声明该平台角色
     */
    public static boolean hasPlatformRole(String roleKey) {
        Jwt jwt = currentJwt();
        if (jwt == null || getUserId() == null) {
            return false;
        }
        List<String> roles = jwt.getClaimAsStringList(JwtConstant.CLAIM_PLATFORM_ROLES);
        return roles != null && roles.contains(roleKey);
    }

    /**
     * 校验Agent任务是否拥有指定操作权限
     * <p>从JWT的agentActions列表判断是否包含目标动作；仅对Agent任务令牌生效</p>
     * @param action 待校验动作，使用 {@link JwtConstant} 中ACTION_*常量
     * @return true=拥有该动作权限；false=无令牌、非Agent、动作不在许可列表
     */
    public static boolean hasAgentAction(String action) {
        Jwt jwt = currentJwt();
        if (!isAgentToken(jwt)) {
            return false;
        }
        List<String> actions = jwt.getClaimAsStringList(JwtConstant.CLAIM_AGENT_ACTIONS);
        return actions != null && actions.contains(action);
    }

    /**
     * 读取JWT中Long类型声明值，做安全类型转换
     * @param claim claim键名，来自 {@link JwtConstant}
     * @return 转换后的Long；无JWT、不存在该claim、值为空返回null
     */
    private static Long getTaskClaim(String claim) {
        Jwt jwt = currentJwt();
        if (!isAgentToken(jwt) || !jwt.hasClaim(claim)) {
            return null;
        }
        Object value = jwt.getClaim(claim);
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    /** 判断当前JWT是否为完整的Agent任务Token。 */
    private static boolean isAgentToken(Jwt jwt) {
        return jwt != null
                && JwtConstant.ACTOR_AGENT.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE))
                && JwtConstant.SCOPE_AGENT.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SCOPE));
    }

    /** 判断当前JWT是否为人类用户Token。 */
    private static boolean isHumanToken(Jwt jwt) {
        return jwt != null
                && JwtConstant.SCOPE_USER.equals(jwt.getClaimAsString(JwtConstant.CLAIM_SCOPE))
                && !JwtConstant.ACTOR_AGENT.equals(jwt.getClaimAsString(JwtConstant.CLAIM_ACTOR_TYPE));
    }
}
