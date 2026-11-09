package com.agentdoc.gateway.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.constant.TaskInternalAuthConstant;
import com.agentdoc.common.logging.LogSanitizer;
import com.agentdoc.common.utils.TaskRecoveryJwtUtils;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.gateway.config.GatewayAuthProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import reactor.core.publisher.Mono;


/**
 * Gateway全局JWT鉴权过滤器，WebFlux响应式全局过滤器 GlobalFilter。
 * <p>
 * 核心职责：
 * <ul>
 * <li>1. 白名单放行：OPTIONS预检请求、配置白名单接口直接跳过JWT校验；</li>
 * <li>2. JWT验签：使用JwtDecoder(JWKS公钥)校验Bearer Token合法性（外部门禁，提前 401 无效流量）；</li>
 * <li>3. 鉴权失败：直接返回401 JSON错误响应，不再转发到后端服务。</li>
 * </ul>
 * <p><strong>不再注入 X-User-* 身份头</strong>：身份由业务服务自行从 Authorization 解析
 * （Spring Security Resource Server），网关只透传原始 Authorization 头。</p>
 * <p>过滤器order=-100，优先级较高，在路由转发、限流之后，转发业务服务之前执行。</p>
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final int FILTER_ORDER = -100;

    /** Authorization Bearer token前缀 */
    private static final String BEARER_PREFIX = JwtConstant.TOKEN_TYPE_BEARER + " ";

    private final JwtDecoder jwtDecoder;
    private final GatewayAuthProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder, GatewayAuthProperties properties) {
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    /**
     * 全局过滤器主逻辑
     * @param exchange 网关请求响应上下文
     * @param chain 过滤器链
     * @return Mono<Void> 响应式执行链
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取请求信息
        ServerHttpRequest request = exchange.getRequest();
        // 获取请求路径
        String path = request.getURI().getPath();
        // 恢复机器入口只能直连内部服务，必须在白名单和 OPTIONS 放行之前拒绝。
        String recoveryPath;
        try {
            // 额外解码一层以拒绝下游可能再次解码的路径；不让编码或 matrix 参数绕过内部入口封禁。
            recoveryPath = StringUtils.cleanPath(UriUtils.decode(path, StandardCharsets.UTF_8)
                    .replaceAll(";[^/]*", "").replaceAll("/{2,}", "/"));
        } catch (IllegalArgumentException exception) {
            return unauthorized(exchange.getResponse(), "非法请求路径");
        }

        /**
         * 路径拦截判断逻辑：拦截一批【内部恢复/内部服务调用专用接口】
         * 核心规则：这类接口是服务间内部通路，禁止经由公共网关转发访问
         * 只允许服务内网直连、内部调用链路访问，不对外暴露在公共网关流量里
         * 触发任意一条条件，就返回 401 拒绝访问，提示：恢复内部入口不通过公共网关开放
         * 匹配分支逐条说明：
         * 1. OnlineCapabilityUtils.internalPath(recoveryPath)
         *    工具方法判断：该路径属于在线能力模块定义的内部路径集合
         * 2. TaskInternalAuthConstant.SIGNING_PATHS.stream().anyMatch(internal ->
         *        recoveryPath.equals(internal) || recoveryPath.startsWith(internal + "/"))
         *    匹配内部签名鉴权路径集合：精确相等 或者 以该前缀开头（子路径全部拦截）
         *    也就是我们前面聊的、需要走 TaskInternalAuth 内部密钥验签的那一组接口
         * 3. pathMatcher.match(TaskInternalAuthConstant.EXECUTION_PROFILE_PATH, recoveryPath)
         *    专门匹配 Task执行档案接口（P0试点的那个内部鉴权接口）
         *    这就是前面 InternalAgentSecurityConfig 保护的执行档案入口
         * 4. recoveryPath.equals("/internal") || recoveryPath.startsWith("/internal/")
         *    拦截 /internal 根路径以及 /internal/ 下全部子资源，通用内部根前缀
         * 5. recoveryPath.startsWith("/api/auth/internal/task-recovery-capabilities")
         *    Auth模块：任务恢复能力查询内部接口前缀
         * 6. recoveryPath.startsWith("/api/auth/internal/task-draft-finalization-capabilities")
         *    Auth模块：任务草稿定稿能力内部接口前缀
         * 7. recoveryPath.startsWith("/api/agent/internal/a2a/")
         *    Agent模块：A2A协议内部互通接口前缀（Agent之间通信）
         * 8. recoveryPath.startsWith("/api/agent/internal/experiment-report/")
         *    Agent模块：实验报表内部拉取接口前缀（评估实验A/B报告，服务内部消费）
         * 9. recoveryPath.startsWith("/api/document/internal/task-drafts/")
         *    Document文档模块：任务草稿内部操作接口前缀
         */
        if (OnlineCapabilityUtils.internalPath(recoveryPath)
                || TaskInternalAuthConstant.SIGNING_PATHS.stream().anyMatch(internal ->
                recoveryPath.equals(internal) || recoveryPath.startsWith(internal + "/"))
                || pathMatcher.match(TaskInternalAuthConstant.EXECUTION_PROFILE_PATH, recoveryPath)
                || recoveryPath.equals("/internal") || recoveryPath.startsWith("/internal/")
                || recoveryPath.startsWith("/api/auth/internal/task-recovery-capabilities")
                || recoveryPath.startsWith("/api/auth/internal/task-draft-finalization-capabilities")
                || recoveryPath.startsWith("/api/agent/internal/a2a/")
                || recoveryPath.startsWith("/api/agent/internal/experiment-report/")
                || recoveryPath.startsWith("/api/document/internal/task-drafts/")) {
            return unauthorized(exchange.getResponse(), "恢复内部入口不通过公共网关开放");
        }
        // -------- 步骤1：OPTIONS跨域预检请求、白名单接口直接放行，跳过鉴权 --------
        if (request.getMethod() == HttpMethod.OPTIONS || isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // -------- 步骤2：解析并校验Bearer JWT Token --------
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange.getResponse(), "缺少或非法的 Authorization 头");
        }
        // 截取Bearer后面的token字符串
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return unauthorized(exchange.getResponse(), "缺少或非法的 Authorization 头");
        }

        try {
            // 使用JWKS公钥解码器验签、解析JWT（无效 token 直接 401，不转发）
            OnlineCapabilityUtils.rejectGeneralAccess(TaskRecoveryJwtUtils.rejectGeneralAccess(jwtDecoder.decode(token)));
        } catch (JwtException e) {
            log.debug("JWT 校验失败, path={}, reason={}", LogSanitizer.sanitizeText(path),
                    LogSanitizer.sanitizeText(e.getMessage()));
            return unauthorized(exchange.getResponse(), "无效的访问令牌");
        }

        // -------- 步骤3：转发（保留原始 Authorization 头，业务服务自行解析身份） --------
        return chain.filter(exchange);
    }

    /**
     * 过滤器执行顺序，-100较高优先级；
     * 需要在限流、路由之后，转发到业务服务之前执行。
     */
    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    /**
     * 判断当前路径是否匹配网关免鉴权白名单，支持ant通配符匹配
     * @param path 请求路径
     * @return true=白名单放行，false=需要校验jwt
     */
    private boolean isWhitelisted(String path) {
        return properties.getWhitelist().stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    /**
     * 构建401未授权JSON响应，网关直接返回，不再转发后端服务
     * @param response ServerHttpResponse
     * @param message 错误提示信息
     * @return Mono<Void>
     */
    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        final byte[] body;
        try {
            // 序列化统一返回Result对象
            body = objectMapper.writeValueAsBytes(Result.fail(ErrorCode.UNAUTHORIZED, message));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }
}
