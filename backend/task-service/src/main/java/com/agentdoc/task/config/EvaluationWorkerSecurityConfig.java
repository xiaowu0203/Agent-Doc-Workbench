package com.agentdoc.task.config;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.task.constant.EvaluationWorkerSecurityConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/** OIDC模式下将Worker专用路径交给现有窄权限验证器，不把Worker凭证分类为人类。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "oidc")
public class EvaluationWorkerSecurityConfig {
    @Bean
    BearerTokenResolver evaluationWorkerBearerTokenResolver() {
        var delegate = new DefaultBearerTokenResolver();
        return request -> {
            if (HttpMethod.POST.matches(request.getMethod())
                    && EvaluationWorkerSecurityConstant.PATHS.contains(request.getServletPath())
                    && request.getHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) != null) {
                return null;
            }
            return delegate.resolve(request);
        };
    }
}
