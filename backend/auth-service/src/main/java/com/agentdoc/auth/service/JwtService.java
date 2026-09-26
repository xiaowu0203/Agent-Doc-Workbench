package com.agentdoc.auth.service;

import com.agentdoc.auth.config.JwtProperties;
import com.agentdoc.auth.constant.AuthConstant;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.common.constant.JwtConstant;
import com.nimbusds.jose.JOSEException;
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
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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
    public String createAccessToken(UserEntity user, List<String> platformRoles) {
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
                .claim(JwtConstant.CLAIM_PLATFORM_ROLES,
                        platformRoles == null ? List.of() : List.copyOf(platformRoles))
                .id(UUID.randomUUID().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * 签发任务能力 JWT（Agent令牌）
     * <p>
     * 任务能力令牌与用户登录令牌复用 auth-service 的 RSA 密钥与 JWKS 端点；
     * 通过 actorType、scope 业务声明和 audience 受众，与普通用户登录令牌做隔离区分。
     * 任务业务权限裁决前置在 task-service，本方法仅负责按既定权限结果组装并签发JWT，不做权限判断。
     * 该令牌放置于请求头 X-TASK-CAPABILITY，供Agent执行任务时调用下游服务使用。
     * </p>
     * @param taskId 任务唯一ID
     * @param agentId Agent实例ID
     * @param spaceId 工作空间ID
     * @param documentId 文档ID
     * @param executionMode 执行模式，标记任务执行策略
     * @param documentVersionSnapshot 文档版本快照序号
     * @param documentContentSha256 文档内容SHA256哈希值
     * @param inputSnapshotSchemaVersion 输入快照协议版本号
     * @param inputSnapshotHash 输入快照规范化哈希，用于校验任务输入不可篡改
     * @param derivationRequestHash Replay/Experiment 派生请求哈希，原始任务为空
     * @param actions 当前任务允许执行的动作集合，下游服务校验动作权限
     * @return 已签名的JWT令牌字符串
     */
    public String createTaskCapabilityToken(Long taskId, Long agentId, Long spaceId,
                                            Long documentId, String executionMode,
                                            Long documentVersionSnapshot, String documentContentSha256,
                                            Integer inputSnapshotSchemaVersion, String inputSnapshotHash,
                                            String derivationRequestHash,
                                            List<String> actions) {
        // 获取当前UTC时间，用于iat签发时间
        Instant now = Instant.now();
        // 构建JWT声明集合
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                // 令牌发行人，对应auth-service的issuer
                .issuer(props.issuer())
                // 签发时间 iat
                .issuedAt(now)
                // 令牌过期时间：基于配置常量设置任务能力令牌TTL
                .expiresAt(now.plus(Duration.ofHours(AuthConstant.TASK_CAPABILITY_TTL_HOURS)))
                // sub主体：使用taskId作为主体标识，代表该令牌归属本次任务
                .subject(String.valueOf(taskId))
                // aud受众：目标接收方，限定为任务能力校验器的受众标识
                .audience(List.of(JwtConstant.TASK_CAPABILITY_AUDIENCE))
                // 自定义声明：actor_type 角色类型 = AGENT，标识这是Agent任务令牌
                .claim(JwtConstant.CLAIM_ACTOR_TYPE, JwtConstant.ACTOR_AGENT)
                // 自定义声明：agentId，关联执行任务的Agent
                .claim(JwtConstant.CLAIM_AGENT_ID, agentId)
                // 自定义声明：taskId，绑定当前任务
                .claim(JwtConstant.CLAIM_TASK_ID, taskId)
                // 自定义声明：spaceId，绑定所属工作空间
                .claim(JwtConstant.CLAIM_SPACE_ID, spaceId)
                // 自定义声明：documentId，绑定操作文档
                .claim(JwtConstant.CLAIM_DOCUMENT_ID, documentId)
                // 自定义声明：executionMode，任务执行模式
                .claim(JwtConstant.CLAIM_EXECUTION_MODE, executionMode)
                // 自定义声明：文档版本快照序号
                .claim(JwtConstant.CLAIM_DOCUMENT_VERSION_SNAPSHOT, documentVersionSnapshot)
                // 自定义声明：文档内容哈希，用于文档内容防篡改校验
                .claim(JwtConstant.CLAIM_DOCUMENT_CONTENT_SHA256, documentContentSha256)
                // 自定义声明：输入快照协议版本
                .claim(JwtConstant.CLAIM_INPUT_SNAPSHOT_SCHEMA_VERSION, inputSnapshotSchemaVersion)
                // 自定义声明：输入快照哈希，校验任务输入快照不可篡改
                .claim(JwtConstant.CLAIM_INPUT_SNAPSHOT_HASH, inputSnapshotHash)
                // 自定义声明：允许动作列表；null转为空列表，避免下游空指针
                .claim(JwtConstant.CLAIM_AGENT_ACTIONS, actions == null ? List.of() : actions)
                // 自定义声明：scope作用域，标记为Agent任务作用域
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_AGENT)
                // jti：全局唯一令牌ID，用于令牌撤销、审计日志
                .id(UUID.randomUUID().toString());
        if (derivationRequestHash != null) {
            builder.claim(JwtConstant.CLAIM_DERIVATION_REQUEST_HASH, derivationRequestHash);
        }
        JwtClaimsSet claims = builder.build();
        // 使用JwtEncoder签名，返回JWT原始字符串
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /**
     * 签发 Evaluation Worker 后台能力M2M令牌
     * <p>
     * 仅供 evaluation-service 使用；用于后台读取数据、对账、精准取消Replay任务集合。
     * 属于服务对服务令牌，actor_type=SERVICE，和Agent任务令牌、用户登录令牌做隔离。
     * </p>
     * @param runId 评估执行实例ID
     * @param spaceId 目标工作空间ID
     * @param taskIdsHash 待处理任务ID集合的哈希，用于标识一批Replay任务
     * @param ttlSeconds 令牌有效时长（单位秒），动态指定，适配评估任务生命周期
     * @param actions 该令牌允许执行的后台操作动作集合
     * @return 已签名JWT令牌字符串
     */
    public String createEvaluationWorkerCapabilityToken(Long runId, Long spaceId, String taskIdsHash,
                                                        long ttlSeconds, List<String> actions) {
        // 获取当前UTC时间，作为iat签发时间
        Instant now = Instant.now();
        // 组装JWT声明载荷
        JwtClaimsSet claims = JwtClaimsSet.builder()
                // 令牌发行人，auth-service issuer
                .issuer(props.issuer())
                // iat：令牌签发时间
                .issuedAt(now)
                // exp：过期时间，使用传入的动态TTL（秒）
                .expiresAt(now.plusSeconds(ttlSeconds))
                // sub主体：固定为evaluation-service标识，代表该令牌归属评估服务
                .subject(JwtConstant.EVALUATION_SERVICE)
                // aud受众：限定为Evaluation Worker能力校验器的受众标识
                .audience(List.of(JwtConstant.EVALUATION_WORKER_CAPABILITY_AUDIENCE))
                // 自定义声明：actor_type = SERVICE，标记为服务身份M2M令牌
                .claim(JwtConstant.CLAIM_ACTOR_TYPE, JwtConstant.ACTOR_SERVICE)
                // 自定义声明：service标识，绑定目标服务evaluation-service
                .claim(JwtConstant.CLAIM_SERVICE, JwtConstant.EVALUATION_SERVICE)
                // 自定义声明：runId，评估执行实例ID
                .claim(JwtConstant.CLAIM_RUN_ID, runId)
                // 自定义声明：spaceId，评估所属工作空间
                .claim(JwtConstant.CLAIM_SPACE_ID, spaceId)
                // 自定义声明：taskIdsHash，一批回放任务集合哈希，用于对账与取消Replay
                .claim(JwtConstant.CLAIM_TASK_IDS_HASH, taskIdsHash)
                // 自定义声明：worker允许执行的后台动作
                .claim(JwtConstant.CLAIM_WORKER_ACTIONS, actions)
                // 自定义声明：scope作用域，服务间调用作用域
                .claim(JwtConstant.CLAIM_SCOPE, JwtConstant.SCOPE_SERVICE)
                // jti：令牌唯一ID，用于审计、令牌失效
                .id(UUID.randomUUID().toString())
                .build();
        // 编码并签名，返回JWT字符串
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
        new SecureRandom().nextBytes(bytes);
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
        } catch (JOSEException ex) {
            throw new IllegalStateException("获取 RSA 公钥失败", ex);
        }
    }
}
