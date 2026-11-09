package com.agentdoc.gateway.poc;

import com.agentdoc.common.utils.AesGcmCrypto;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.session.web.server.session.SpringSessionWebSessionStore;
import org.springframework.web.server.session.WebSessionStore;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** 仅供P0验证：独立Redis命名空间、测试密钥和加密序列化，不是正式存储实现。 */
final class P0RedisSessionStore implements AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    final String namespace;
    final LettuceConnectionFactory connection;
    final ReactiveRedisTemplate<String, byte[]> raw;
    final WebSessionStore sessions;

    P0RedisSessionStore(String namespace, byte[] key) {
        this.namespace = namespace;
        RedisStandaloneConfiguration endpoint = new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("POC_REDIS_HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("POC_REDIS_PORT", "6379")));
        String password = System.getenv("POC_REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            endpoint.setPassword(password);
        }
        connection = new LettuceConnectionFactory(endpoint,
                LettuceClientConfiguration.builder().commandTimeout(TIMEOUT).build());
        connection.afterPropertiesSet();
        connection.start();
        RedisSerializationContext<String, Object> encrypted = RedisSerializationContext
                .<String, Object>newSerializationContext(RedisSerializer.string())
                .value(new EncryptedSerializer(key)).hashKey(RedisSerializer.string())
                .hashValue(new EncryptedSerializer(key)).build();
        ReactiveRedisSessionRepository repository = new ReactiveRedisSessionRepository(new ReactiveRedisTemplate<>(connection, encrypted));
        repository.setRedisKeyNamespace(namespace);
        repository.setDefaultMaxInactiveInterval(P0BffPocConfiguration.WINDOW);
        sessions = new SpringSessionWebSessionStore<>(repository);
        RedisSerializationContext<String, byte[]> bytes = RedisSerializationContext
                .<String, byte[]>newSerializationContext(RedisSerializer.string()).value(RedisSerializer.byteArray())
                .hashKey(RedisSerializer.string()).hashValue(RedisSerializer.byteArray()).build();
        raw = new ReactiveRedisTemplate<>(connection, bytes);
    }

    void clearFixture() {
        // 只扫描本次随机命名空间，禁止清库或枚举业务会话。
        raw.scan(ScanOptions.scanOptions().match(namespace + ":*").count(100).build())
                .flatMap(raw::delete).then().block(TIMEOUT);
    }

    @Override
    public void close() {
        connection.destroy();
    }

    /** AES-GCM校验通过后才反序列化；JDK格式仅验证框架对象可恢复，正式方案需另行评审。 */
    static final class EncryptedSerializer implements RedisSerializer<Object> {
        private final AesGcmCrypto crypto;
        private final JdkSerializationRedisSerializer delegate = new JdkSerializationRedisSerializer();

        EncryptedSerializer(byte[] key) {
            crypto = new AesGcmCrypto(Base64.getEncoder().encodeToString(key));
        }

        @Override
        public byte[] serialize(Object value) throws SerializationException {
            if (value == null) { return null; }
            try {
                return crypto.encrypt(Base64.getEncoder().encodeToString(delegate.serialize(value)))
                        .getBytes(StandardCharsets.UTF_8);
            } catch (IllegalArgumentException | IllegalStateException error) {
                throw new SerializationException("P0 session encryption failed", error);
            }
        }

        @Override
        public Object deserialize(byte[] value) throws SerializationException {
            // Spring Session删除属性时会写入空值；它不表示可接受的加密认证对象。
            if (value == null || value.length == 0) { return null; }
            try {
                String decrypted = crypto.decrypt(new String(value, StandardCharsets.UTF_8));
                if (decrypted == null) { throw new SerializationException("Invalid P0 encrypted session"); }
                return delegate.deserialize(Base64.getDecoder().decode(decrypted));
            } catch (IllegalArgumentException | IllegalStateException error) {
                throw new SerializationException("P0 session authentication failed", error);
            }
        }
    }
}
