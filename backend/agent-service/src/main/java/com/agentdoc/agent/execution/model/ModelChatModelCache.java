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
 * 按模型 ID 和配置版本缓存 ChatModel，并在 LRU 淘汰或配置失效时释放模型资源。
 */
@Component
public class ModelChatModelCache {

    private final int maxSize;
    private final Map<ModelCacheKey, ChatModel> entries = new LinkedHashMap<>(16, 0.75f, true);

    public ModelChatModelCache(@Value("${agent-doc.agent.model.chat-model-cache.max-size:100}") int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("模型 ChatModel 缓存容量必须大于 0");
        }
        this.maxSize = maxSize;
    }

    public synchronized ChatModel getOrCreate(Long modelId, Long configVersion,
                                               Supplier<ChatModel> factory) {
        ModelCacheKey key = new ModelCacheKey(modelId, configVersion);
        ChatModel cached = entries.get(key);
        if (cached != null) {
            return cached;
        }

        ChatModel created = Objects.requireNonNull(factory.get(), "ChatModel 构建结果不能为空");
        entries.put(key, created);
        if (entries.size() > maxSize) {
            Iterator<Map.Entry<ModelCacheKey, ChatModel>> iterator = entries.entrySet().iterator();
            Map.Entry<ModelCacheKey, ChatModel> eldest = iterator.next();
            iterator.remove();
            closeQuietly(eldest.getValue());
        }
        return created;
    }

    /** 使指定模型的全部配置版本失效，避免配置更新后继续复用旧实例。 */
    public synchronized void invalidate(Long modelId) {
        Iterator<Map.Entry<ModelCacheKey, ChatModel>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ModelCacheKey, ChatModel> entry = iterator.next();
            if (entry.getKey().modelId().equals(modelId)) {
                iterator.remove();
                closeQuietly(entry.getValue());
            }
        }
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized boolean contains(Long modelId, Long configVersion) {
        return entries.containsKey(new ModelCacheKey(modelId, configVersion));
    }

    @PreDestroy
    public synchronized void close() {
        entries.values().forEach(this::closeQuietly);
        entries.clear();
    }

    private void closeQuietly(ChatModel chatModel) {
        if (chatModel instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 释放失败不能阻断其他缓存实例的淘汰和服务关闭流程。
            }
        }
    }

    record ModelCacheKey(Long modelId, Long configVersion) {
        ModelCacheKey {
            Objects.requireNonNull(modelId, "模型 ID 不能为空");
            Objects.requireNonNull(configVersion, "模型配置版本不能为空");
        }
    }
}
