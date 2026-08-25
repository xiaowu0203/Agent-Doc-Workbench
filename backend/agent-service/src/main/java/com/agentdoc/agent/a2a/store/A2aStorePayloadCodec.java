package com.agentdoc.agent.a2a.store;

import com.agentdoc.agent.security.AgentConfigCryptoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * A2A存储载荷编解码器
 * <p>
 * 负责A2A领域对象持久化的序列化/反序列化，同时对存储内容做加密保护；
 * 流程：对象 → JSON字符串 → 加密密文字符串（存入数据库）；
 * 读取流程：密文字符串解密得到JSON → 反序列化为Java对象。
 * </p>
 * <p>
 * ObjectMapper使用copy副本，独立配置，不影响全局ObjectMapper；
 * 关闭日期自动时区调整，保证存储与读取时间戳一致性。
 * </p>
 */
@Component
public class A2aStorePayloadCodec {
    /** 独立的JSON序列化实例，针对存储场景做定制配置 */
    private final ObjectMapper objectMapper;
    /** 配置与载荷加密解密服务 */
    private final AgentConfigCryptoService cryptoService;

    /**
     * @param objectMapper Spring全局ObjectMapper
     * @param cryptoService 加密解密服务
     */
    public A2aStorePayloadCodec(ObjectMapper objectMapper, AgentConfigCryptoService cryptoService) {
        // copy一份实例，避免修改全局ObjectMapper配置
        this.objectMapper = objectMapper.copy()
                // 关闭日期自动适配上下文时区，防止存、读时间发生偏移
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        this.cryptoService = cryptoService;
    }

    /**
     * 对象编码：序列化为JSON，再加密得到密文，用于存入数据库
     *
     * @param value 待序列化的A2A业务对象
     * @return 加密后的密文字符串
     * @throws IllegalStateException JSON序列化失败时抛出
     */
    public String encode(Object value) {
        // 对象转JSON字符串，交由加密服务加密后返回密文
        try {
            return cryptoService.encrypt(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("A2A 协议对象序列化失败", exception);
        }
    }

    /**
     * 载荷解码：密文解密得到JSON，再反序列化为目标对象
     *
     * @param payload 数据库取出的加密密文字符串
     * @param type 目标对象Class类型
     * @return 反序列化后的业务对象
     * @param <T> 泛型目标类型
     * @throws IllegalStateException JSON反序列化失败抛出
     */
    public <T> T decode(String payload, Class<T> type) {
        try {
            // 先解密得到明文JSON，再转为Java对象
            return objectMapper.readValue(cryptoService.decrypt(payload), type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("A2A 协议对象反序列化失败", exception);
        }
    }
}
