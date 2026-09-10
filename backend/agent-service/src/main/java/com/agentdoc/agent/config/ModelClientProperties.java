package com.agentdoc.agent.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 模型 HTTP 客户端配置。
 */
@Validated
@ConfigurationProperties(prefix = "agent-doc.agent.model.client")
public class ModelClientProperties {

    /** 模型响应读取超时。 */
    @NotNull
    private Duration readTimeout = Duration.ofMinutes(10);

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * @return 超时配置是否为正数
     */
    @AssertTrue(message = "模型响应读取超时必须大于 0")
    public boolean isReadTimeoutValid() {
        return readTimeout != null && !readTimeout.isZero() && !readTimeout.isNegative();
    }
}
