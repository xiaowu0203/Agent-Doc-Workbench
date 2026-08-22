package com.agentdoc.auth.service;

import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.auth.constant.AuthConstant;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.common.constant.JwtConstant;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.micrometer.common.util.StringUtils;
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
 * JWT签发服务，Auth‑Service内部使用，负责RSA密钥加载、Access‑Token生成、JWK集合输出。
 * <p>
 * 核心设计：
 * <ul>
 * <li>支持配置持久化RSA公私钥；未配置时自动生成临时RSA‑2048密钥对；</li>
 * <li>Access‑Token：RSA‑RS256签名JWT短时效令牌；网关通过JWKS接口拉取公钥做验签；</li>
 * <li>Refresh‑Token：随机不透明字符串，本身不是JWT，映射关系存储在Redis，支持主动撤销；</li>
 * <li>对外提供JWK集合，供网关 /oauth2/jwks 端点输出公钥集合；</li>
 * </ul>
 * <p>
 * 重要提醒：临时密钥模式下，服务重启密钥会重新生成，历史所有Access‑Token全部失效；。
 */
@Slf4j
@Component
public class JwtService {

    private final JwtProperties props;
    private final RSAKey rsaKey;
    private final JwtEncoder encoder;

    /**
     * 构造器：加载RSA密钥，初始化JWT编码器。
     * @param props jwt配置参数（公私钥文本、issuer、ttl等）
     */
    public JwtService(JwtProperties props) {
        this.props = props;
        // 解析/生成RSA密钥
        this.rsaKey = resolveRsaKey(props);
        // 构建JWK源，用于NimbusJwtEncoder签名
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        this.encoder = new NimbusJwtEncoder(jwkSource);
    }

    /**
     * 解析RSA密钥：优先读取配置中的公私钥PEM文本；配置缺失则生成临时内存密钥。
     * @param props jwt配置
     * @return RSAKey nimbus封装的RSA密钥对象（同时包含公钥、私钥）
     */
    private RSAKey resolveRsaKey(JwtProperties props) {
        // 如果配置文件已经配置公私钥PEM，则使用配置密钥
        if (StringUtils.isNotBlank(props.privateKey())
                && StringUtils.isNotBlank(props.publicKey())) {
            try {
                RSAPrivateKey privateKey = parsePrivateKey(props.privateKey());
                RSAPublicKey publicKey = parsePublicKey(props.publicKey());
                log.info("使用配置的 RSA 密钥");
                // keyID随机生成，网关JWKS会依据kid做密钥匹配
                return new RSAKey.Builder(publicKey).privateKey(privateKey)
                        .keyID(UUID.randomUUID().toString()).build();
            } catch (Exception ex) {
                throw new IllegalStateException("解析配置的 RSA 密钥失败", ex);
            }
        }
        // 未配置密钥，启动生成临时密钥，仅适合开发调试
        log.warn("未配置 JWT RSA 密钥，启动时自动生成临时密钥（重启后旧 Token 失效）");
        return generateEphemeralKey();
    }

    /**
     * 生成临时内存RSA‑2048密钥对，仅开发环境使用。
     * @return RSAKey 封装密钥对象
     */
    private RSAKey generateEphemeralKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(AuthConstant.RSA_KEY_SIZE);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey).privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString()).build();
        } catch (Exception ex) {
            throw new IllegalStateException("生成 RSA 密钥失败", ex);
        }
    }

    /**
     * 解析PEM格式RSA私钥，PKCS#8格式。
     * @param pem 私钥PEM文本，包含BEGIN/END标记
     * @return RSAPrivateKey java原生私钥对象
     * @throws Exception 解析异常
     */
    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = decodePem(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /**
     * 解析PEM格式RSA公钥，X509格式。
     * @param pem 公钥PEM文本
     * @return RSAPublicKey java原生公钥对象
     * @throws Exception 解析异常
     */
    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = decodePem(pem);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(der));
    }

    /**
     * PEM解码：剔除BEGIN/END标记、换行空格，Base64解码得到DER二进制密钥。
     * @param pem pem字符串
     * @return der二进制字节数组
     */
    private byte[] decodePem(String pem) {
        String cleaned = pem.replace(AuthConstant.PEM_PRIVATE_KEY_BEGIN, "")
                .replace(AuthConstant.PEM_PRIVATE_KEY_END, "")
                .replace(AuthConstant.PEM_PUBLIC_KEY_BEGIN, "")
                .replace(AuthConstant.PEM_PUBLIC_KEY_END, "")
                .replaceAll("\\s", "");
        if (cleaned.matches("^[A-Za-z0-9+/=]+$")) {
            return Base64.getDecoder().decode(cleaned);
        }
        // 可能是 Base64 URL 或未换行文本，按普通 Base64 解码
        return Base64.getDecoder().decode(cleaned);
    }

    /**
     * 生成Access Token，RSA‑RS256签名JWT。
     * <p>
     * payload包含：iss签发者、sub=userId、username、nickname、scope、jti；设置过期时间。
     * @param user 用户实体
     * @return JWT字符串
     */
    public String createAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .claim(JwtConstant.CLAIM_USERNAME, user.getUsername())
                .claim(JwtConstant.CLAIM_NICKNAME, user.getNickname() == null ? user.getUsername() : user.getNickname())
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_USER)
                .id(UUID.randomUUID().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * 生成Refresh‑Token：安全随机字节，Base64‑URL无填充编码。
     * <p>
     * 注意：refreshToken不是JWT，只是一串不透明随机字符串；真实信息保存在Redis。
     * @return refreshToken字符串
     */
    public String createRefreshToken() {
        byte[] bytes = new byte[AuthConstant.REFRESH_TOKEN_BYTE_LENGTH];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 获取仅公钥的JWK对象，用于JWKS端点对外发布。
     * @return RSAKey 公钥JWK
     */
    public RSAKey publicJwk() {
        return rsaKey.toPublicJWK();
    }

    /**
     * 构建JWKSet公钥集合，用于 /oauth2/jwks 接口输出，供网关远程拉取验签公钥。
     * @return JWKSet
     */
    public JWKSet jwkSet() {
        return new JWKSet(publicJwk());
    }

    /**
     * 获取jwt配置对象。
     * @return JwtProperties
     */
    public JwtProperties props() {
        return props;
    }

    /**
     * 获取Java原生RSA公钥。
     * @return RSAPublicKey
     */
    public RSAPublicKey getPublicKey() {
        try {
            return rsaKey.toRSAPublicKey();
        } catch (com.nimbusds.jose.JOSEException ex) {
            throw new IllegalStateException("获取 RSA 公钥失败", ex);
        }
    }
}
