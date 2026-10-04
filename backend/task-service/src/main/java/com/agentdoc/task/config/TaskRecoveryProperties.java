package com.agentdoc.task.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static com.agentdoc.common.constant.TaskRecoveryConstant.MIN_MACHINE_KEY_BYTES;
import static com.agentdoc.common.constant.TaskRecoveryConstant.MAX_CONNECT_TIMEOUT_MS;
import static com.agentdoc.common.constant.TaskRecoveryConstant.MAX_READ_TIMEOUT_MS;

/** 终态恢复部署配置，默认关闭；不改变 Task 领域状态。 */
@Data
@Component
@ConfigurationProperties(prefix = "agent-doc.task-recovery")
public class TaskRecoveryProperties {
    /** 是否自动申请恢复窄凭证；空密钥时仍不可用。 */
    private boolean enabled;
    /** 当前专属机器密钥，只经环境变量/被忽略配置注入。 */
    private String machineKey = "";
    /** auth-service 内部直连地址。 */
    private String authUrl = "http://localhost:8081";
    /** agent-service 内部直连地址。 */
    private String agentUrl = "http://localhost:8084";
    /** document-service 内部直连地址。 */
    private String documentUrl = "http://localhost:8082";
    /** 运维明确确认内部网络有等价加密时才设为 true，不代表普通 HTTP 安全。 */
    private boolean trustedEncryptedNetwork;
    /** 单次连接超时毫秒；最长 2 秒，以保持处理预算小于锁租期。 */
    private int connectTimeoutMs = MAX_CONNECT_TIMEOUT_MS;
    /** 单次读取超时毫秒；最长 5 秒。 */
    private int readTimeoutMs = MAX_READ_TIMEOUT_MS;

    public boolean isConfigured() {
        return enabled && machineKey != null && machineKey.getBytes(StandardCharsets.UTF_8).length >= MIN_MACHINE_KEY_BYTES;
    }

    @PostConstruct
    void validate() {
        if (connectTimeoutMs <= 0 || connectTimeoutMs > MAX_CONNECT_TIMEOUT_MS || readTimeoutMs <= 0 || readTimeoutMs > MAX_READ_TIMEOUT_MS) {
            throw new IllegalArgumentException("恢复连接/读取超时超出 2 秒/5 秒预算");
        }
        if (enabled) {
            for (String endpoint : new String[]{authUrl, agentUrl, documentUrl}) {
                URI uri = URI.create(endpoint);
                String host = uri.getHost();
                boolean loopback = host != null && Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(host);
                if (uri.getUserInfo() != null || uri.getQuery() != null || host == null
                        || !("https".equals(uri.getScheme())
                        || ("http".equals(uri.getScheme()) && (loopback || trustedEncryptedNetwork)))) {
                    throw new IllegalArgumentException("恢复内部地址必须使用 HTTPS、loopback 或经确认的等价加密网络");
                }
            }
        }
    }

    @Override
    public String toString() { return "TaskRecoveryProperties[密钥已隐藏]"; }
}
