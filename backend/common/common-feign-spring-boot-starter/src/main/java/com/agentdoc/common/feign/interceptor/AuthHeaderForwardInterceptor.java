package com.agentdoc.common.feign.interceptor;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.feign.context.AuthorizationContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求拦截器：透传下游调用 Authorization 请求头
 *
 * <p>头优先级逻辑：
 * <ol>
 * <li>优先从 Web Servlet 请求上下文透传原始 Authorization（用户登录JWT）；</li>
 * <li>若Web上下文无Authorization，尝试从 {@link AuthorizationContext} 获取手动设置的授权Token；</li>
 * <li>追加任务短时能力令牌 {@link HeaderConstants#X_TASK_CAPABILITY}，用于MQ异步任务线程身份鉴权。</li>
 * </ol>
 *
 * 关键约束与边界：
 * <ul>
 * <li>Web请求线程：可通过 {@link RequestContextHolder} 拿到Servlet上下文，自动透传用户Authorization；</li>
 * <li>异步/MQ消费线程（@Async、RabbitMQ消费者、自建线程池）：Servlet上下文丢失，
 *     无法获取原始Web请求头，依赖 {@link AuthorizationContext}、{@link TaskCapabilityContext} 手动绑定身份；</li>
 * <li>Authorization为空时不会向下游设置空Header，避免无效鉴权；</li>
 * <li>下游 document‑service 通过 X‑Task‑Capability 解析任务短时能力JWT，完成Agent异步任务的权限校验。</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 * <li>同步HTTP接口：透传登录用户JWT；</li>
 * <li>MQ异步任务执行：线程内设置TaskCapabilityContext，向下游传递任务能力令牌，实现Agent身份访问文档服务。</li>
 */
public class AuthHeaderForwardInterceptor implements RequestInterceptor {

    /**
     * Feign请求执行前回调，填充鉴权相关请求头
     * <p>执行顺序：先取Servlet Web上下文Authorization
     * → 再取AuthorizationContext
     * → 最后追加任务能力头X‑Task‑Capability</p>
     * @param template Feign请求模板，用于修改Header、请求参数
     */
    @Override
    public void apply(RequestTemplate template) {
        // 1. 尝试从Servlet Web请求上下文获取原始Authorization（同步Web请求场景）
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

        // 2. 若尚未设置Authorization，从线程上下文获取授权Token（适配异步/MQ线程，无Servlet上下文）
        if (!template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
            String authorization = AuthorizationContext.current();
            if (authorization != null && !authorization.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, authorization);
            }
        }

        // 3. 追加任务短时能力令牌头 X‑Task‑Capability
        // MQ异步任务执行时，TaskCapabilityContext持有task签发的短时能力JWT，供下游document‑service鉴权使用
        String capability = TaskCapabilityContext.current();
        if (capability != null && !capability.isBlank()) {
            template.header(HeaderConstants.X_TASK_CAPABILITY, capability);
        }
    }
}
