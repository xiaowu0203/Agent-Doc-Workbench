package com.agentdoc.common.web;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.context.LoginUser;
import com.agentdoc.common.context.UserContext;
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
 * 用户上下文过滤器：接收网关透传的 X-User-* 请求头并填充 UserContext。
 * 由 common-web-spring-boot-starter（CommonWebAutoConfiguration）自动装配注册，业务服务无需自行实现。
 */
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String userId = request.getHeader(HeaderConstants.X_USER_ID);
            if (userId != null && !userId.isBlank()) {
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
            UserContext.clear();
        }
    }

    private Long parseLong(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private Set<String> splitScopes(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                result.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }
}
