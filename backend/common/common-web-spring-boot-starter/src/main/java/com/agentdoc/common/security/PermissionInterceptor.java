package com.agentdoc.common.security;

import com.agentdoc.common.api.ErrorCode;
import com.agentdoc.common.context.UserContext;
import com.agentdoc.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器，处理 {@link RequireLogin}、{@link RequirePermission} 注解登录校验。
 * <p>
 * 逻辑规则：
 * <ul>
 * <li>方法或Controller类上存在 {@link RequireLogin} / {@link RequirePermission}，则要求用户必须登录；</li>
 * <li>{@link RequirePermission} 隐含登录要求；</li>
 * <li>未标记任意鉴权注解的接口直接放行，支持匿名访问；</li>
 * <li>仅做登录身份校验，<strong>权限值校验不在本类实现</strong>；</li>
 * <li>未登录抛出 {@link BusinessException}，由全局异常处理器输出401；</li>
 * <li>只对Controller接口生效，内部调用、@Async异步不会进入拦截器逻辑。</li>
 * </ul>
 * <p>注意：直接使用 handlerMethod 自带注解判断，不处理组合注解、继承注解场景。
 * 如果需要支持组合注解，请改用 {@link org.springframework.core.annotation.AnnotatedElementUtils}。
 * </p>
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
            // 判断：方法上 或 Controller类上，存在 RequireLogin / RequirePermission 任意注解，则需要登录
            boolean requiresLogin = handlerMethod.hasMethodAnnotation(RequireLogin.class)
                    || handlerMethod.hasMethodAnnotation(RequirePermission.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(RequireLogin.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(RequirePermission.class);

            // 需要登录，但当前上下文无登录用户，抛出未登录异常
            if (requiresLogin && !UserContext.isLoggedIn()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
        }
        return true;
    }
}
