package com.agentdoc.common.feign.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求拦截器：透传下游调用 Authorization 请求头
 * 注意事项：
 * 1. 依赖 {@link RequestContextHolder}，必须保证Feign调用在Web请求线程内执行；
 * 2. 异步线程（@Async、新线程池）中 RequestContextHolder 会丢失上下文，此拦截器无法拿到header；
 * 3. 仅当原始请求携带Authorization头时才进行透传，为空则不设置。
 */
public class AuthHeaderForwardInterceptor implements RequestInterceptor {

    /**
     * Feign请求执行前回调，修改请求模板，追加Authorization请求头
     * @param template Feign请求模板，用于设置header、参数等
     */
    @Override
    public void apply(RequestTemplate template) {
        // 获取当前Web请求上下文
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        // 判断是否为Servlet Web请求上下文
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            // 提取原始请求的Authorization头部（JWT Token）
            String authorization = servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            // 头部非空，则设置到Feign下游请求模板中，实现头透传
            if (authorization != null && !authorization.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        }
    }
}
