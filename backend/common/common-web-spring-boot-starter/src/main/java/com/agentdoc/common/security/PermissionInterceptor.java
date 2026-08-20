package com.agentdoc.common.security;

import com.agentdoc.common.api.ErrorCode;
import com.agentdoc.common.context.UserContext;
import com.agentdoc.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 权限拦截器：校验 @RequireLogin / @RequirePermission 注解的接口要求登录态。
 * 依赖 spring-webmvc（optional 依赖），由 CommonMvcAutoConfiguration 注册，仅 Servlet MVC 服务生效。
 */
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            boolean requiresLogin = handlerMethod.hasMethodAnnotation(RequireLogin.class)
                    || handlerMethod.hasMethodAnnotation(RequirePermission.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(RequireLogin.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(RequirePermission.class);
            if (requiresLogin && !UserContext.isLoggedIn()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
        }
        return true;
    }
}
