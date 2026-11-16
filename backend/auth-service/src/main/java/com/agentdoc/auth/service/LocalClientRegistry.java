package com.agentdoc.auth.service;

import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.auth.enums.LocalClientPurpose;
import com.agentdoc.auth.security.LocalClientAuthentication;
import com.agentdoc.common.constant.IdentityTokenConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/** 只从部署受管配置取机器身份；认证签发入口不访问用户表或本人Feign。 */
@Service
@ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalClientRegistry {
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_SECRET_BYTES = 512;
    private final LocalIdentityProperties properties;
    private final byte[] unknownSecret = new byte[MIN_SECRET_BYTES];

    public LocalClientRegistry(LocalIdentityProperties properties) {
        this.properties = properties;
        new SecureRandom().nextBytes(unknownSecret);
        Set<String> secrets = new HashSet<>();
        properties.getClients().forEach((name, client) -> {
            if (!name.matches(IdentityTokenConstant.SERVICE_CLIENT_PATTERN) || client.getPurpose() == null
                    || (IdentityTokenConstant.BFF_CLIENT.equals(name) != (client.getPurpose() == LocalClientPurpose.BFF))
                    || client.getSecret() == null || client.getSecret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES
                    || client.getSecret().getBytes(StandardCharsets.UTF_8).length > MAX_SECRET_BYTES
                    || !secrets.add(client.getSecret())) {
                throw new IllegalStateException("本地机器客户端配置无效或复用了Secret");
            }
        });
        if (!properties.getClients().containsKey(IdentityTokenConstant.BFF_CLIENT)) {
            throw new IllegalStateException("local模式必须登记workbench-web的独立BFF凭据");
        }
    }

    public LocalClientAuthentication authenticate(String name, String secret) {
        var client = properties.getClients().get(name);
        byte[] expected = client == null ? unknownSecret : client.getSecret().getBytes(StandardCharsets.UTF_8);
        boolean matches = MessageDigest.isEqual(digest(secret.getBytes(StandardCharsets.UTF_8)), digest(expected));
        if (!matches || client == null || !client.isEnabled()) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
        return new LocalClientAuthentication(name, client.getPurpose());
    }

    /** 用于Spring Security方法权限，不能由普通JWT的name/authority冒充。 */
    public boolean isCurrentBff() { return currentHasPurpose(LocalClientPurpose.BFF); }
    public boolean isCurrentService() { return currentHasPurpose(LocalClientPurpose.SERVICE); }

    public String currentClient() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof LocalClientAuthentication local && local.isAuthenticated()) { return local.getName(); }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    /** 停用Secret不从消费允许表抹去client，既有证明仍受固定exp/偏差限制。 */
    public Set<String> registeredServices() {
        Set<String> result = new HashSet<>();
        properties.getClients().forEach((name, client) -> { if (client.getPurpose() == LocalClientPurpose.SERVICE) { result.add(name); } });
        return Set.copyOf(result);
    }

    private boolean currentHasPurpose(LocalClientPurpose purpose) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof LocalClientAuthentication local) || !local.isAuthenticated() || !local.hasPurpose(purpose)) { return false; }
        var client = properties.getClients().get(local.getName());
        return client != null && client.isEnabled() && client.getPurpose() == purpose;
    }

    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256不可用"); }
    }
}
