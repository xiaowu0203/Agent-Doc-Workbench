package com.agentdoc.common.web;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.context.LoginUser;
import com.agentdoc.common.context.UserContext;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 用户上下文过滤器：接收网关透传的 X‑User‑* 系列请求头，解析并构建 {@link LoginUser}，存入 {@link UserContext}。
 * <p>
 * 工作流程：
 * <ul>
 * <li>网关已经完成JWT签名校验，把登录主体信息序列化放到HTTP请求头向下游透传；</li>
 * <li>下游业务服务读取请求头组装 {@link LoginUser}，绑定到当前请求线程上下文；</li>
 * <li>请求结束在finally强制清空上下文，避免Tomcat线程池复用造成用户信息串请求；</li>
 * <li>请求头 userId 为空时，代表匿名请求，UserContext保持空。</li>
 * </ul>
 * <strong>仅Servlet(SpringMVC)环境生效，WebFlux网关不使用本过滤器</strong>。
 * <p>安全注意：本过滤器<strong>不做身份校验</strong>，信任上游网关的请求头；
 * 业务服务对外暴露时必须确保外部请求无法直接携带 X‑User‑* 请求头。
 */
public class UserContextFilter extends OncePerRequestFilter {
    /**
     * 过滤器核心处理逻辑。
     * @param request HttpServletRequest 请求，读取网关透传的X‑User‑*请求头
     * @param response HttpServletResponse 响应
     * @param filterChain 过滤器链，继续向下执行
     * @throws ServletException servlet异常
     * @throws IOException IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String userId = request.getHeader(HeaderConstants.X_USER_ID);
            // userId不为空，代表网关识别出已登录用户，组装LoginUser放入上下文
            if (StringUtils.isNotBlank(userId)) {
                UserContext.set(new LoginUser(
                        Long.valueOf(userId),
                        request.getHeader(HeaderConstants.X_USER_NAME),
                        request.getHeader(HeaderConstants.X_USER_NICKNAME),
                        parseLong(request.getHeader(HeaderConstants.X_AGENT_ID)),
                        splitScopes(request.getHeader(HeaderConstants.X_USER_SCOPES))
                ));
            }
            filterChain.doFilter(request, response);
        } finally {
            // 清理线程上下文，防止线程复用导致用户信息泄露、串号
            UserContext.clear();
        }
    }

    /**
     * 将字符串转为Long；空白/Null返回null。
     * @param value 原始header字符串
     * @return 转换后的Long，空输入返回null
     * @throws NumberFormatException 字符串非合法数字抛出
     */
    private Long parseLong(String value) {
        return StringUtils.isBlank(value) ? null : Long.valueOf(value);
    }

    /**
     * 分割逗号分隔的scope字符串，去空格并转小写，生成权限集合。
     * @param value header中scope原始逗号字符串
     * @return 权限集合；null/空白返回空不可变集合
     */
    private Set<String> splitScopes(String value) {
        if (StringUtils.isBlank(value)) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String part : value.split(",")) {
            if (StringUtils.isNotBlank(part)) {
                result.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }
}
