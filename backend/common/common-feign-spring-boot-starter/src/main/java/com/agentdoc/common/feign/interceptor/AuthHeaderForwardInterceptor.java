package com.agentdoc.common.feign.interceptor;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.constant.TaskRecoveryConstant;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.constant.TaskInternalAuthConstant;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.AuthIdentityFeign;
import com.agentdoc.common.feign.config.TaskInternalCallProperties;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.feign.context.AuthorizationContext;
import com.agentdoc.common.utils.ReportProofSignature;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Feign 请求拦截器：透传下游调用 Authorization 请求头
 *
 * <p>头优先级逻辑：
 * <ol>
 * <li>登记的Task内部契约使用独立机器凭据直连，排除用户与任务凭据；</li>
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
 * </ul>
 */
public class AuthHeaderForwardInterceptor implements RequestInterceptor {

    // 回环地址白名单，用于判断本地可信地址
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    // Task内部调用配置（secret、authUrl、agentUrl、trustedEncryptedNetwork）
    private final TaskInternalCallProperties internalProperties;
    // identity身份链路是否信任加密内网开关
    private final boolean identityTrustedEncryptedNetwork;
    // Ant路径匹配器，用于匹配执行档案路径
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthHeaderForwardInterceptor() {
        this(new TaskInternalCallProperties());
    }

    public AuthHeaderForwardInterceptor(TaskInternalCallProperties internalProperties) {
        this(internalProperties, false);
    }

    public AuthHeaderForwardInterceptor(TaskInternalCallProperties internalProperties, boolean identityTrustedEncryptedNetwork) {
        this.internalProperties = internalProperties;
        this.identityTrustedEncryptedNetwork = identityTrustedEncryptedNetwork;
    }

    /**
     * Feign请求执行前回调，填充鉴权相关请求头
     * <p>执行顺序：先取Servlet Web上下文Authorization
     * → 再取AuthorizationContext
     * → 最后追加任务能力头X‑Task‑Capability</p>
     * @param template Feign请求模板，用于修改Header、请求参数
     */
    @Override
    public void apply(RequestTemplate template) {
        // 身份查询链路专属保护逻辑，命中则直接返回，不再走后续透传逻辑
        if (applyIdentitySelfProtection(template)) {
            return;
        }
        // Task内部机器凭据调用链路，命中则替换目标地址+设置Basic机器凭据，不再透传用户token
        if (applyTaskInternalAuthentication(template)) {
            return;
        }
        // 专用恢复调用不透传用户、Agent 或已过期能力凭证，避免混合身份和凭证泄漏。
        if (template.headers().keySet().stream().anyMatch(key -> TaskRecoveryConstant.MACHINE_KEY_HEADER.equalsIgnoreCase(key)
                || OnlineCapabilityConstant.HEADER.equalsIgnoreCase(key)
                || ReportProofSignature.HEADER.equalsIgnoreCase(key)
                || TaskRecoveryConstant.CAPABILITY_HEADER.equalsIgnoreCase(key))) {
            template.removeHeader(HttpHeaders.AUTHORIZATION);
            template.removeHeader(HeaderConstants.X_TASK_CAPABILITY);
            return;
        }
        // 1. 尝试从Servlet Web请求上下文获取原始Authorization（同步Web请求场景）
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        // 判断是否为Servlet Web请求上下文
        if (!template.headers().containsKey(HttpHeaders.AUTHORIZATION)
                && attributes instanceof ServletRequestAttributes servletAttributes) {
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

    /**
     * 身份查询仅携带显式用户Bearer，禁止向未受信地址发送。
     * 校验目标地址安全策略、Authorization格式，清理无关能力/机器头，命中返回true
     */
    private boolean applyIdentitySelfProtection(RequestTemplate template) {
        // 只针对 AuthIdentityFeign 这个身份查询Feign客户端生效
        if (template.feignTarget() == null || template.feignTarget().type() != AuthIdentityFeign.class) {
            return false;
        }
        URI target = URI.create(template.feignTarget().url());
        // 安全校验：只允许HTTPS，或者http+回环/信任加密内网；禁止携带userInfo、query、fragment
        if (target.getHost() == null || target.getUserInfo() != null || target.getQuery() != null || target.getFragment() != null
                || !("https".equalsIgnoreCase(target.getScheme()) || ("http".equalsIgnoreCase(target.getScheme())
                && (LOOPBACK_HOSTS.contains(target.getHost()) || identityTrustedEncryptedNetwork)))) {
            throw new IllegalStateException("身份查询必须使用HTTPS、本机回环或明确受信加密网络");
        }
        // 身份查询要求必须有且仅有一个Bearer Token
        var authorization = template.headers().get(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.size() != 1
                || !authorization.iterator().next().startsWith("Bearer ")) {
            throw new IllegalStateException("身份查询必须显式提供唯一用户Bearer");
        }
        // 清理所有机器、任务能力类头部，身份链路只保留用户Bearer
        template.removeHeader(HeaderConstants.X_TASK_CAPABILITY);
        template.removeHeader(TaskRecoveryConstant.MACHINE_KEY_HEADER);
        template.removeHeader(TaskRecoveryConstant.CAPABILITY_HEADER);
        template.removeHeader(OnlineCapabilityConstant.HEADER);
        template.removeHeader(ReportProofSignature.HEADER);
        return true;
    }

    /**
     * 只给登记契约的三个内部入口附加机器身份，不从当前用户上下文取凭据。
     * 匹配AuthFeign签发路径 / AgentFeign执行档案路径；替换直连地址、使用Basic机器secret鉴权，清理用户token
     */
    private boolean applyTaskInternalAuthentication(RequestTemplate template) {
        if (template.feignTarget() == null) {
            return false;
        }
        Class<?> type = template.feignTarget().type();
        String url;
        // 匹配：AuthFeign POST + 内部签名路径 → 使用authUrl直连地址
        if (type == AuthFeign.class && "POST".equals(template.method())
                && TaskInternalAuthConstant.SIGNING_PATHS.contains(template.path())) {
            url = internalProperties.getAuthUrl();
        }
        // 匹配：AgentFeign GET + 执行档案路径 → 使用agentUrl直连地址
        else if (type == AgentFeign.class && "GET".equals(template.method())
                && pathMatcher.match(TaskInternalAuthConstant.EXECUTION_PROFILE_PATH, template.path())) {
            url = internalProperties.getAgentUrl();
        } else {
            return false;
        }
        // 校验secret长度，防止密钥过短不安全
        if (internalProperties.getSecret().getBytes(StandardCharsets.UTF_8).length
                < TaskInternalAuthConstant.MIN_SECRET_BYTES) {
            throw new IllegalStateException("Task内部调用缺少独立机器凭据");
        }
        URI target = URI.create(url);
        // 同样安全校验：仅允许HTTPS或可信内网http，禁止userInfo、query、fragment
        if (target.getHost() == null || target.getUserInfo() != null || target.getQuery() != null
                || target.getFragment() != null || !("https".equalsIgnoreCase(target.getScheme())
                || ("http".equalsIgnoreCase(target.getScheme())
                && (LOOPBACK_HOSTS.contains(target.getHost()) || internalProperties.isTrustedEncryptedNetwork())))) {
            throw new IllegalStateException("Task内部调用必须使用HTTPS、本机回环或明确受信加密网络");
        }
        // 覆盖Feign目标为直连地址（不走注册中心负载均衡）
        template.target(url);
        // 清除用户Authorization、任务能力头，改用独立机器凭据
        template.removeHeader(HttpHeaders.AUTHORIZATION);
        template.removeHeader(HeaderConstants.X_TASK_CAPABILITY);
        template.removeHeader(TaskRecoveryConstant.MACHINE_KEY_HEADER);
        template.removeHeader(TaskRecoveryConstant.CAPABILITY_HEADER);
        template.removeHeader(OnlineCapabilityConstant.HEADER);
        template.removeHeader(ReportProofSignature.HEADER);
        // 设置Basic Auth，使用内部独立机器密钥
        template.header(HttpHeaders.AUTHORIZATION, "Basic " + HttpHeaders.encodeBasicAuth(
                TaskInternalAuthConstant.CLIENT_ID, internalProperties.getSecret(), StandardCharsets.UTF_8));
        return true;
    }
}
