package com.agentdoc.gateway.security;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.context.LoginUser;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.utils.JwtTokenParser;
import com.agentdoc.gateway.config.GatewayAuthProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.util.StringUtils;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Gateway全局JWT鉴权过滤器，WebFlux响应式全局过滤器 GlobalFilter。
 * <p>
 * 核心职责：
 * <ul>
 * <li>1. 安全防护：强制剥离客户端传入的X‑User‑*等敏感请求头，防止客户端伪造身份；</li>
 * <li>2. 链路追踪：生成/透传 X‑Trace‑Id 追踪ID，向下游服务传递；</li>
 * <li>3. 白名单放行：OPTIONS预检请求、配置白名单接口直接跳过JWT校验；</li>
 * <li>4. JWT验签：使用JwtDecoder(JWKS公钥)校验Bearer Token合法性；</li>
 * <li>5. 身份透传：校验通过后，网关写入X‑User‑*系列请求头，传递登录用户信息给下游业务服务；</li>
 * <li>6. 鉴权失败：直接返回401 JSON错误响应，不再转发到后端服务。</li>
 * </ul>
 * <p>
 * 安全关键点：下游业务服务<strong>禁止信任客户端原始请求头</strong>，只信任网关写入的X‑*头；
 * 过滤器order=-100，优先级较高，在路由转发、限流之后，转发业务服务之前执行。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    /**
     * 需要强制剥离的风险请求头列表。
     * 这些头部只能由网关写入，客户端原始请求携带全部要删除，防止伪造用户身份。
     */
    private static final List<String> FORBIDDEN_HEADERS = List.of(
            HeaderConstants.X_USER_ID,
            HeaderConstants.X_USER_NAME,
            HeaderConstants.X_USER_NICKNAME,
            HeaderConstants.X_AGENT_ID,
            HeaderConstants.X_USER_SCOPES,
            HeaderConstants.X_TRACE_ID
    );

    /** Authorization Bearer token前缀 */
    private static final String BEARER_PREFIX = JwtConstant.TOKEN_TYPE_BEARER + " ";

    private final JwtDecoder jwtDecoder;
    private final GatewayAuthProperties properties;
    private final JwtTokenParser jwtTokenParser = new JwtTokenParser();
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
        // 获取客户端传入的traceId
        String inboundTraceId = request.getHeaders().getFirst(HeaderConstants.X_TRACE_ID);
        // 客户端没传则生成全新traceId，否则复用传入值
        String traceId = StringUtils.isBlank(inboundTraceId)
                ? UUID.randomUUID().toString().replace("-", "")
                : inboundTraceId;

        // -------- 步骤1：清理危险请求头，注入链路追踪ID --------
        ServerHttpRequest stripped = request.mutate().headers(headers -> {
            // 删除客户端提交的所有禁止的X‑*头，杜绝伪造身份
            FORBIDDEN_HEADERS.forEach(headers::remove);
            // 设置网关管控的追踪ID
            headers.set(HeaderConstants.X_TRACE_ID, traceId);
        }).build();
        ServerWebExchange cleaned = exchange.mutate().request(stripped).build();

        // -------- 步骤2：OPTIONS跨域预检请求、白名单接口直接放行，跳过鉴权 --------
        if (request.getMethod() == HttpMethod.OPTIONS || isWhitelisted(path)) {
            return chain.filter(cleaned);
        }

        // -------- 步骤3：解析并校验Bearer JWT Token --------
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(cleaned.getResponse(), "缺少或非法的 Authorization 头");
        }
        // 截取Bearer后面的token字符串
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return unauthorized(cleaned.getResponse(), "缺少或非法的 Authorization 头");
        }

        final Jwt jwt;
        try {
            // 使用JWKS公钥解码器验签、解析JWT
            jwt = jwtTokenParser.decode(token, jwtDecoder);
        } catch (JwtException e) {
            log.debug("JWT 校验失败, path={}, reason={}", path, e.getMessage());
            // token无效：过期、签名错误、格式错误，返回401
            return unauthorized(cleaned.getResponse(), "无效的访问令牌");
        }

        // -------- 步骤4：解析JWT载荷，把用户身份写入请求头向下游透传 --------
        LoginUser user = jwtTokenParser.toLoginUser(jwt);
        ServerHttpRequest enriched = stripped.mutate().headers(headers -> {
            headers.set(HeaderConstants.X_USER_ID, String.valueOf(user.userId()));
            headers.set(HeaderConstants.X_USER_NAME, user.username());
            headers.set(HeaderConstants.X_USER_NICKNAME, user.nickname());
            // 如果是代理账号，设置agentId
            if (user.isAgent()) {
                headers.set(HeaderConstants.X_AGENT_ID, String.valueOf(user.agentId()));
            }
            // 用户权限作用域，逗号拼接向下游传递
            if (user.scopes() != null && !user.scopes().isEmpty()) {
                headers.set(HeaderConstants.X_USER_SCOPES, String.join(",", user.scopes()));
            }
            // 注意：原始Authorization头会保留，下游服务可做二次校验
        }).build();
        // 把增强后的请求交给过滤器链继续执行，转发到后端服务
        return chain.filter(exchange.mutate().request(enriched).build());
    }

    /**
     * 过滤器执行顺序，-100较高优先级；
     * 需要在限流、路由之后，转发到业务服务之前执行。
     */
    @Override
    public int getOrder() {
        return -100;
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
