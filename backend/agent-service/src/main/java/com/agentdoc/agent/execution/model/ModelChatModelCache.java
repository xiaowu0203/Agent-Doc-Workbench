package com.agentdoc.agent.execution.model;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * ChatModel实例缓存
 * <p>
 * 缓存各个厂商Spring‑AI {@link ChatModel}实例，避免频繁重复创建SDK客户端，减少连接、认证开销。
 * 缓存key由 {@code modelId + configVersion} 组成；配置版本变化会生成新key，旧缓存条目会被淘汰。
 * </p>
 * <p>
 * 缓存策略：
 * <ul>
 *     <li>基于访问顺序LRU：使用access‑order=true的{@link LinkedHashMap}，最近读取的条目放到链表尾部；超maxSize时淘汰最久未访问的头部条目</li>
 *     <li>线程安全：全部对外方法加 {@code synchronized}</li>
 *     <li>资源自动释放：淘汰、invalidate、服务销毁时，如果ChatModel实现{@link AutoCloseable}，调用close释放底层SDK网络资源（如Google GenAI Client）</li>
 * </ul>
 * <p>
 * 重要约束：
 * <ol>
 * <li>缓存的ChatModel实例<strong>禁止修改内部defaultOptions</strong>；任务级动态参数全部通过requestOptions在Prompt维度传入，防止跨任务污染缓存实例。</li>
 * <li>key必须带上configVersion：模型配置修改更新configVersion，旧缓存自动失效，不会复用旧API‑Key/baseUrl等配置。</li>
 * <li>淘汰时调用{@link #closeQuietly}，关闭异常只静默吃掉，不阻断整体淘汰/服务关闭流程。</li>
 * </ol>
 */
@Component
public class ModelChatModelCache {

    /** 缓存最大条目数 */
    private final int maxSize;

    /**
     * 缓存存储容器，access‑order=true：访问时重排顺序，实现LRU最近最少访问淘汰
     * key：{@link ModelCacheKey}(modelId,configVersion)
     * value：各厂商ChatModel实例（可能实现AutoCloseable）
     */
    private final Map<ModelCacheKey, ChatModel> entries = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * 构造器读取配置最大缓存容量
     * @param maxSize 最大可缓存ChatModel实例数量，必须>0
     * @throws IllegalArgumentException maxSize小于等于0抛出参数非法异常
     */
    public ModelChatModelCache(@Value("${agent-doc.agent.model.chat-model-cache.max-size:100}") int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("模型 ChatModel 缓存容量必须大于 0");
        }
        this.maxSize = maxSize;
    }

    /**
     * 获取或创建ChatModel实例；LRU缓存主入口
     * <p>
     * 1. 根据modelId+configVersion组装缓存key；命中直接返回缓存实例；
     * 2. 未命中调用factory工厂函数构建新ChatModel；
     * 3. 放入缓存；如果超过maxSize，淘汰最久未访问条目，被淘汰实例若实现AutoCloseable则执行资源释放。
     * </p>
     * @param modelId 模型主键ID
     * @param configVersion 模型配置版本号，配置变更版本号会递增
     * @param factory ChatModel工厂，缓存未命中时执行构建厂商ChatModel实例
     * @return 可复用ChatModel实例
     * @throws NullPointerException factory返回null时抛出
     */
    public synchronized ChatModel getOrCreate(Long modelId, Long configVersion,
                                               Supplier<ChatModel> factory) {
        // 构建模型缓存Key
        ModelCacheKey key = new ModelCacheKey(modelId, configVersion);
        // 获取模型
        ChatModel cached = entries.get(key);
        // 不为空则直接返回
        if (cached != null) {
            return cached;
        }

        // 工厂构建新实例，禁止返回null
        ChatModel created = Objects.requireNonNull(factory.get(), "ChatModel 构建结果不能为空");
        entries.put(key, created);

        // 超出容量，淘汰LRU头部（最久没有被访问）
        if (entries.size() > maxSize) {
            Iterator<Map.Entry<ModelCacheKey, ChatModel>> iterator = entries.entrySet().iterator();
            Map.Entry<ModelCacheKey, ChatModel> eldest = iterator.next();
            iterator.remove();
            // 淘汰时释放底层SDK资源
            closeQuietly(eldest.getValue());
        }
        return created;
    }

    /**
     * 使指定modelId下<strong>全部configVersion</strong>缓存条目失效。
     * <p>场景：模型配置编辑更新，主动清理该模型所有旧ChatModel实例；被移除的实例会执行资源释放。</p>
     * @param modelId 需要失效的模型主键ID
     */
    public synchronized void invalidate(Long modelId) {
        // 获取模型迭代器
        Iterator<Map.Entry<ModelCacheKey, ChatModel>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ModelCacheKey, ChatModel> entry = iterator.next();
            // 相同模型Id则进行移除
            if (entry.getKey().modelId().equals(modelId)) {
                iterator.remove();
                // 安静关闭ChatModel资源
                closeQuietly(entry.getValue());
            }
        }
    }

    /**
     * 获取当前缓存内实例数量，仅供监控/单元测试使用
     * @return 缓存条目个数
     */
    synchronized int size() {
        return entries.size();
    }

    /**
     * 判断指定(modelId+configVersion)是否已经存在缓存，仅供监控/单元测试
     * @param modelId 模型id
     * @param configVersion 配置版本
     * @return true已存在缓存；false未命中
     */
    synchronized boolean contains(Long modelId, Long configVersion) {
        return entries.containsKey(new ModelCacheKey(modelId, configVersion));
    }

    /**
     * 服务销毁回调；Spring容器关闭，释放全部缓存中AutoCloseable的ChatModel资源，清空缓存map
     */
    @PreDestroy
    public synchronized void close() {
        entries.values().forEach(this::closeQuietly);
        entries.clear();
    }

    /**
     * 安静关闭ChatModel资源；如果实现AutoCloseable则执行close，出现异常直接忽略不向外抛出。
     * <p>用于缓存淘汰、invalidate、容器销毁场景；关闭失败不能阻断整体流程。</p>
     * @param chatModel 待释放的ChatModel实例，可以为非AutoCloseable实现
     */
    private void closeQuietly(ChatModel chatModel) {
        if (chatModel instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 释放失败不能阻断其他缓存实例的淘汰和服务关闭流程。
            }
        }
    }

    /**
     * 缓存Key记录类
     * <p>
     * 组合主键：modelId + configVersion。
     * <b>configVersion参与equals/hashCode：模型配置修改后版本号变化，自动生成新缓存key，旧实例不会复用。</b>
     * 构造做非空校验，不允许modelId/configVersion为null。
     * </p>
     * @param modelId 模型主键
     * @param configVersion 模型配置版本号
     */
    record ModelCacheKey(Long modelId, Long configVersion) {
        ModelCacheKey {
            Objects.requireNonNull(modelId, "模型 ID 不能为空");
            Objects.requireNonNull(configVersion, "模型配置版本不能为空");
        }
    }
}
