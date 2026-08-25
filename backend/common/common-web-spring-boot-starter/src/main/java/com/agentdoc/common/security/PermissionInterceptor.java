package com.agentdoc.common.security;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器，处理 {@link RequireLogin} 注解登录校验。
 * <p>
 * 逻辑规则：
 * <ul>
 * <li>方法或Controller类上存在 {@link RequireLogin}，则要求用户必须登录；</li>
 * <li>未标记鉴权注解的接口直接放行，支持匿名访问（注解驱动）；</li>
 * <li>登录状态取自 Spring Security {@link SecurityContextHolder}（Resource Server 已解析 JWT）；
 * 需排除匿名 token，且要求已认证；</li>
 * <li>未登录抛出 {@link BusinessException}，由全局异常处理器输出 401；</li>
 * <li>只对Controller接口生效，内部调用、@Async异步不会进入拦截器逻辑。</li>
 * </ul>
 * <p>细粒度权限（空间成员角色等）不在本拦截器实现，由业务层 {@code SpacePermissionService} 显式校验；
 * 未来如需方法级权限声明，使用 Spring Security 标准 {@code @PreAuthorize}。</p>
 * <p>注意：无效/过期 token 已在 Security filter 层直接 401，不会到达本拦截器。</p>
 */
public class PermissionInterceptor implements HandlerInterceptor {

    /**
     * 请求预处理，完成登录鉴权校验。
     * @param request Http请求
     * @param response Http响应
     * @param handler 请求处理器
     * @return true 继续执行后续流程
     * @throws BusinessException 未登录时抛出，错误码 {@link ErrorCode#UNAUTHORIZED}
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 仅处理Controller方法，跳过静态资源等其他handler
        if (handler instanceof HandlerMethod handlerMethod) {
            // 判断：方法上 或 Controller类上，存在 RequireLogin 注解，则需要登录
            boolean requiresLogin = handlerMethod.hasMethodAnnotation(RequireLogin.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(RequireLogin.class);

            // 需要登录，但 SecurityContext 无已认证主体，抛出未登录异常
            if (requiresLogin && !isAuthenticated()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
        }
        return true;
    }

    /**
     * 判断当前请求是否已认证（排除匿名 token）。
     * @return true 已登录
     */
    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.isAuthenticated();
    }
}
