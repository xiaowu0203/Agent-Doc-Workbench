package com.agentdoc.auth.security;

import com.agentdoc.auth.entity.UserEntity;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * JWT 签发服务：RS256。
 * 未配置密钥时启动自动生成临时密钥（开发用，重启后旧 Token 失效）。
 */
@Slf4j
@Component
public class JwtService {

    private final JwtProperties props;
    private final RSAKey rsaKey;
    private final JwtEncoder encoder;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.rsaKey = resolveRsaKey(props);
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        this.encoder = new NimbusJwtEncoder(jwkSource);
    }

    private RSAKey resolveRsaKey(JwtProperties props) {
        if (props.privateKey() != null && !props.privateKey().isBlank()
                && props.publicKey() != null && !props.publicKey().isBlank()) {
            try {
                RSAPrivateKey privateKey = parsePrivateKey(props.privateKey());
                RSAPublicKey publicKey = parsePublicKey(props.publicKey());
                log.info("使用配置的 RSA 密钥");
                return new RSAKey.Builder(publicKey).privateKey(privateKey)
                        .keyID(UUID.randomUUID().toString()).build();
            } catch (Exception ex) {
                throw new IllegalStateException("解析配置的 RSA 密钥失败", ex);
            }
        }
        log.warn("未配置 JWT RSA 密钥，启动时自动生成临时密钥（重启后旧 Token 失效）");
        return generateEphemeralKey();
    }

    private RSAKey generateEphemeralKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey).privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString()).build();
        } catch (Exception ex) {
            throw new IllegalStateException("生成 RSA 密钥失败", ex);
        }
    }

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = decodePem(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = decodePem(pem);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    private byte[] decodePem(String pem) {
        String cleaned = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        if (cleaned.matches("^[A-Za-z0-9+/=]+$")) {
            return Base64.getDecoder().decode(cleaned);
        }
        // 可能是 Base64 URL 或未换行文本，按普通 Base64 解码
        return Base64.getDecoder().decode(cleaned);
    }

    /**
     * 生成 Access Token。
     */
    public String createAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname() == null ? user.getUsername() : user.getNickname())
                .claim("scope", "user")
                .id(UUID.randomUUID().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * 生成不透明 Refresh Token（随机值，映射关系存 Redis）。
     */
    public String createRefreshToken() {
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 仅含公钥的 JWK，用于 JWKS 端点分发。
     */
    public RSAKey publicJwk() {
        return rsaKey.toPublicJWK();
    }

    public JWKSet jwkSet() {
        return new JWKSet(publicJwk());
    }

    public JwtProperties props() {
        return props;
    }

    public RSAPublicKey getPublicKey() {
        try {
            return rsaKey.toRSAPublicKey();
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException("获取 RSA 公钥失败", ex);
        }
    }
}