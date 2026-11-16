package com.agentdoc.task.config;

import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.task.constant.EvaluationWorkerSecurityConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * 两种认证模式共用的Worker专用接口适配
 * 设计目标：
 * Worker 节点（评估执行Worker）走独立凭证体系，不能被框架当成【人类用户的OIDC身份】来解析；
 * 将 Worker 专用路径剥离出默认OIDC BearerToken解析逻辑，交给项目已有的【窄权限验证器】单独校验，
 * 避免 Worker 凭证被 OIDC 过滤器误解析、误归类为人类登录身份，造成身份校验错乱。
 * local与oidc都加载，避免Worker证明进入任一模式的人类解析器。
 * proxyBeanMethods = false：@Configuration 轻量模式，跳过CGLIB代理，提升启动性能，无Bean互相依赖场景推荐开启
 */
@Configuration(proxyBeanMethods = false)
public class EvaluationWorkerSecurityConfig {

    /**
     * 自定义 BearerToken 解析器，替换Spring Security默认的Token提取逻辑
     * 逻辑说明：
     * Spring Security OIDC默认逻辑：自动从 Authorization: Bearer xxx 头提取token，送入OIDC鉴权链，当成用户身份处理
     * 本解析器做拦截分支：
     * 当同时满足下面全部条件时 → 返回 null：告诉Security：【这个请求不要提取Bearer token，不走OIDC身份解析链路】
     *  1. 请求方法是 POST
     *  2. 请求路径命中 Worker 专属路径集合 EvaluationWorkerSecurityConstant.PATHS
     *  3. 请求携带标记头 X-EVALUATION-WORKER-CAPABILITY（Worker调用的特征标识头）
     * 其他所有请求（普通用户、其他接口）→ 交给原生 DefaultBearerTokenResolver 正常解析Bearer Token，走原有OIDC登录鉴权
     * 返回null的效果：
     * OIDC 的 Jwt/OpaqueToken 过滤器不会拿到token、不会执行OIDC校验；
     * 请求继续往下流转，由我们业务层专门的 Worker 窄权限验证器接管鉴权（Worker自有凭证/签名校验）
     * @return BearerTokenResolver 自定义Token解析组件
     */
    @Bean
    BearerTokenResolver evaluationWorkerBearerTokenResolver() {
        // 委托Spring原生默认Bearer解析实现（从Authorization Bearer头拿token）
        var delegate = new DefaultBearerTokenResolver();
        return request -> {
            // 命中Worker特征请求：POST + Worker路径 + Worker特征头
            if (HttpMethod.POST.matches(request.getMethod())
                    && EvaluationWorkerSecurityConstant.PATHS.contains(request.getServletPath())
                    && request.getHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) != null) {
                // 返回null：停止OIDC侧Bearer提取，跳过OIDC身份校验链路
                return null;
            }
            // 非Worker请求，保持原有OIDC Bearer解析逻辑不变
            return delegate.resolve(request);
        };
    }
}
