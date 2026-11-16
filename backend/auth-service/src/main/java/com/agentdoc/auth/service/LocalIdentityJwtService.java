package com.agentdoc.auth.service;

import com.agentdoc.auth.config.LocalIdentityProperties;
import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.common.constant.IdentityTokenConstant;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.utils.LocalIdentityTokenUtils;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 正式local身份签名域；不复用Capability密钥、不生成临时生产私钥、不签Refresh。 */
@Service
@ConditionalOnProperty(prefix = "workbench.auth", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalIdentityJwtService {
    private static final int MIN_RSA_BITS = 2048;
    private final RSAKey key;
    private final JwtEncoder encoder;
    private final Clock clock;

    public LocalIdentityJwtService(LocalIdentityProperties properties, JwtService capabilities, Clock clock) {
        this.clock = clock;
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der(properties.getPrivateKey(), "PRIVATE KEY")));
            var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(der(properties.getPublicKey(), "PUBLIC KEY")));
            if (publicKey.getModulus().bitLength() < MIN_RSA_BITS || !publicKey.getModulus().equals(privateKey.getModulus())
                    || Arrays.equals(publicKey.getEncoded(), capabilities.getPublicKey().getEncoded())) {
                throw new IllegalArgumentException();
            }
            RSAKey rsa = new RSAKey.Builder(publicKey).privateKey(privateKey).algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build();
            key = new RSAKey.Builder(rsa).keyID(IdentityTokenConstant.LOCAL_KEY_PREFIX + rsa.computeThumbprint()).build();
            encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        } catch (Exception error) {
            throw new IllegalStateException("本地身份RSA密钥缺失、无效或复用了Capability密钥");
        }
    }

    public IssuedCredential issueHuman(UserEntity user) {
        if (user == null || user.getId() == null || user.getId() <= 0 || !UserStatus.isEnabled(user.getStatus())
                || user.getAuthVersion() == null || user.getAuthVersion() < 0) {
            throw new IllegalArgumentException("不能为无效人类状态签发凭据");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        String session = UUID.randomUUID().toString();
        JwtClaimsSet claims = base(now, now.plus(IdentityTokenConstant.HUMAN_MAX_LIFETIME), user.getId().toString(),
                IdentityTokenConstant.BFF_CLIENT, IdentityTokenConstant.HUMAN_AUDIENCE, IdentityTokenConstant.HUMAN_SCOPE,
                IdentityTokenConstant.HUMAN_PURPOSE, JwtConstant.ACTOR_HUMAN)
                .id(session).claim(IdentityTokenConstant.CLAIM_SESSION_ID, session)
                .claim(IdentityTokenConstant.CLAIM_AUTH_VERSION, user.getAuthVersion()).build();
        return encode(IdentityTokenConstant.HUMAN_JOSE_TYPE, claims, session);
    }

    public IssuedCredential issueService(String client) {
        if (client == null || !client.matches(IdentityTokenConstant.SERVICE_CLIENT_PATTERN) || IdentityTokenConstant.BFF_CLIENT.equals(client)) {
            throw new IllegalArgumentException("无效的服务client");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return encode(IdentityTokenConstant.SERVICE_JOSE_TYPE,
                base(now, now.plus(IdentityTokenConstant.SERVICE_MAX_LIFETIME), client, client,
                        IdentityTokenConstant.SERVICE_AUDIENCE, IdentityTokenConstant.SERVICE_SCOPE,
                        IdentityTokenConstant.SERVICE_PURPOSE, JwtConstant.ACTOR_SERVICE).id(UUID.randomUUID().toString()).build(), null);
    }

    public JwtDecoder humanDecoder() { return decoder(null); }
    public JwtDecoder serviceDecoder(Set<String> allowedClients) { return decoder(Set.copyOf(allowedClients)); }
    public Map<String, Object> publicJwks() { return new JWKSet(key.toPublicJWK()).toJSONObject(); }

    private JwtDecoder decoder(Set<String> services) {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey())
                    .signatureAlgorithm(SignatureAlgorithm.RS256).validateType(false).build();
            decoder.setJwtValidator(jwt -> {
                try {
                    if (!key.getKeyID().equals(jwt.getHeaders().get("kid"))) { throw new BadJwtException("未知身份kid"); }
                    if (services == null) { LocalIdentityTokenUtils.requireHuman(jwt, clock.instant()); }
                    else { LocalIdentityTokenUtils.requireService(jwt, clock.instant(), services); }
                    return OAuth2TokenValidatorResult.success();
                } catch (IllegalArgumentException | BadJwtException error) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "无效的本地身份凭据", null));
                }
            });
            return decoder;
        } catch (Exception error) { throw new IllegalStateException("本地身份验签器初始化失败"); }
    }

    private IssuedCredential encode(String type, JwtClaimsSet claims, String session) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type(type).keyId(key.getKeyID()).build();
        return new IssuedCredential(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
                claims.getIssuedAt(), claims.getExpiresAt(), session);
    }

    private JwtClaimsSet.Builder base(Instant now, Instant expires, String subject, String client, String audience,
                                      String scope, String purpose, String actor) {
        return JwtClaimsSet.builder().issuer(IdentityTokenConstant.LOCAL_ISSUER).subject(subject).issuedAt(now).notBefore(now)
                .expiresAt(expires).audience(List.of(audience)).claim("azp", client).claim(JwtConstant.CLAIM_SCOPE, scope)
                .claim(IdentityTokenConstant.CLAIM_PURPOSE, purpose).claim(JwtConstant.CLAIM_ACTOR_TYPE, actor);
    }

    private static byte[] der(String pem, String type) {
        return Base64.getDecoder().decode(pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "").replaceAll("\\s", ""));
    }

    /** @param value 仅受控内部协议返回的凭据 @param issuedAt 固定认证起点 @param expiresAt 绝对截止 @param sessionId 人类会话关联，服务为空 */
    public record IssuedCredential(String value, Instant issuedAt, Instant expiresAt, String sessionId) {
        @Override public String toString() { return "[REDACTED IDENTITY CREDENTIAL]"; }
    }
}
