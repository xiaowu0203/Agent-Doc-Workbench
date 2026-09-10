package com.agentdoc.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 浏览器认证 Cookie 配置。
 *
 * @param secure 是否只允许 HTTPS 传输；生产环境必须开启
 * @param sameSite 跨站发送策略
 */
@ConfigurationProperties(prefix = "auth.cookie")
public record AuthCookieProperties(boolean secure, String sameSite) {
}
