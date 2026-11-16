package com.agentdoc.auth.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.auth.service.JwtService;
import com.agentdoc.auth.service.LocalIdentityJwtService;
import java.time.Clock;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/** 仅测试类路径加载；不读取/写入真实身份私钥，正式代码不提供临时密钥回退。 */
public class LocalIdentityTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final KeyPair IDENTITY_KEYS = keys();
    public static final KeyPair CAPABILITY_KEYS = keys();
    public static final String BFF_SECRET = secret();
    public static final String SERVICE_SECRET = secret();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if ("oidc".equalsIgnoreCase(context.getEnvironment().getProperty("workbench.auth.mode", "local"))) { return; }
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("p1-local-test-identities", Map.of(
                "auth.identity.private-key", pem("PRIVATE KEY", IDENTITY_KEYS.getPrivate().getEncoded()),
                "auth.identity.public-key", pem("PUBLIC KEY", IDENTITY_KEYS.getPublic().getEncoded()),
                "auth.jwt.private-key", pem("PRIVATE KEY", CAPABILITY_KEYS.getPrivate().getEncoded()),
                "auth.jwt.public-key", pem("PUBLIC KEY", CAPABILITY_KEYS.getPublic().getEncoded()),
                "auth.identity.clients.workbench-web.purpose", "BFF",
                "auth.identity.clients.workbench-web.secret", BFF_SECRET,
                "auth.identity.clients.task-service.purpose", "SERVICE",
                "auth.identity.clients.task-service.secret", SERVICE_SECRET)));
    }

    public static KeyPair keys() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (Exception error) { throw new IllegalStateException("测试RSA密钥生成失败"); }
    }

    public static LocalIdentityJwtService signer(JwtService capabilities) {
        var properties = new LocalIdentityProperties();
        properties.setPrivateKey(pem("PRIVATE KEY", IDENTITY_KEYS.getPrivate().getEncoded()));
        properties.setPublicKey(pem("PUBLIC KEY", IDENTITY_KEYS.getPublic().getEncoded()));
        return new LocalIdentityJwtService(properties, capabilities, Clock.systemUTC());
    }
    public static String pem(String type, byte[] data) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(data) + "\n-----END " + type + "-----";
    }
    private static String secret() { byte[] value = new byte[32]; new SecureRandom().nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
}
