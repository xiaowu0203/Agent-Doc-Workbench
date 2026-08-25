package com.agentdoc.agent.a2a.store;

import com.agentdoc.agent.mapper.A2aPushConfigMapper;
import com.agentdoc.agent.pojo.entity.A2aPushConfigEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL实现的A2A推送通知配置存储
 * <p>
 * 实现A2A协议定义的 {@link PushNotificationConfigStore}，负责任务推送回调配置的持久化；
 * 使用数据库表 a2a_push_config 存储，完整配置对象通过 {@link A2aStorePayloadCodec} 加密后存入encryptedPayload字段；
 * 支持新增/更新、分页查询、删除、协议版本读取；采用基于Token的分页，而非数字页码。
 * </p>
 * <p>主键逻辑：configId为配置唯一标识，未指定时复用taskId作为configId；
 * upsert执行插入或更新，保证同一个configId在表中唯一。</p>
 */
@Component
@RequiredArgsConstructor
public class MySqlA2aPushConfigStore implements PushNotificationConfigStore {

    private final A2aPushConfigMapper mapper;
    /** 载荷编解码器：对象 ↔ 加密JSON字符串，用于存储完整TaskPushNotificationConfig */
    private final A2aStorePayloadCodec codec;

    /**
     * 保存推送配置，不指定协议版本（重载）
     * @param config 推送回调配置对象
     * @return 保存后的配置对象
     */
    @Override
    public TaskPushNotificationConfig setInfo(TaskPushNotificationConfig config) {
        return setInfo(config, null);
    }

    /**
     * 新增或更新任务推送回调配置
     * <p>执行逻辑：确定configId → 构建Entity → 对象加密存入encryptedPayload → upsert幂等写入数据库。</p>
     * @param config 推送配置实体
     * @param protocolVersion A2A推送协议版本，null则解析为默认版本
     * @return 写入存储后的完整配置对象
     */
    @Override
    public TaskPushNotificationConfig setInfo(TaskPushNotificationConfig config, String protocolVersion) {
        // 如果配置未提供id，则使用taskId作为configId
        String configId = config.id() == null || config.id().isBlank() ? config.taskId() : config.id();
        TaskPushNotificationConfig stored = TaskPushNotificationConfig.builder(config).id(configId).build();
        A2aPushConfigEntity entity = new A2aPushConfigEntity();
        entity.setConfigId(configId);
        entity.setTaskId(stored.taskId());
        entity.setProtocolVersion(PushNotificationConfigStore.resolveProtocolVersion(protocolVersion));
        entity.setEncryptedPayload(codec.encode(stored));
        entity.setUpdatedAt(LocalDateTime.now());
        // 幂等：存在则更新，不存在则插入
        mapper.upsert(entity);
        return stored;
    }

    /**
     * 根据taskId分页查询该任务下全部推送配置
     * <p>分页方式：游标分页，pageToken为上一页最后一条的configId；
     * 返回结果包含当前页数据与下一页nextToken。</p>
     * @param params 列表查询参数，携带taskId、pageToken、页大小
     * @return 分页结果：当前页集合 + 下一页游标token
     */
    @Override
    public ListTaskPushNotificationConfigsResult getInfo(ListTaskPushNotificationConfigsParams params) {
        // 查询该taskId全部推送配置，解密反序列化为领域对象
        List<TaskPushNotificationConfig> configs = mapper.selectList(
                        new LambdaQueryWrapper<A2aPushConfigEntity>()
                                .eq(A2aPushConfigEntity::getTaskId, params.id())
                                .orderByAsc(A2aPushConfigEntity::getConfigId))
                .stream()
                .map(entity -> codec.decode(entity.getEncryptedPayload(), TaskPushNotificationConfig.class))
                .toList();
        // 根据pageToken计算分页起始下标
        int start = pageStart(configs, params.pageToken());
        int pageSize = params.getEffectivePageSize();
        int end = Math.min(start + pageSize, configs.size());
        // 下一页token：还有剩余数据就取end位置的id，无剩余返回null
        String nextToken = end < configs.size() ? configs.get(end).id() : null;
        return new ListTaskPushNotificationConfigsResult(configs.subList(start, end), nextToken);
    }

    /**
     * 删除指定任务下的推送配置
     * @param taskId A2A任务ID
     * @param configId 推送配置ID；传null则使用taskId作为configId
     */
    @Override
    public void deleteInfo(String taskId, String configId) {
        String resolvedId = configId == null ? taskId : configId;
        mapper.delete(new LambdaQueryWrapper<A2aPushConfigEntity>()
                .eq(A2aPushConfigEntity::getTaskId, taskId)
                .eq(A2aPushConfigEntity::getConfigId, resolvedId));
    }

    /**
     * 获取单条推送配置对应的推送协议版本
     * @param taskId A2A任务ID
     * @param configId 推送配置ID
     * @return 解析后的协议版本，记录不存在或taskId不匹配返回默认版本
     */
    @Override
    public String getProtocolVersion(String taskId, String configId) {
        A2aPushConfigEntity entity = mapper.selectById(configId);
        return entity == null || !taskId.equals(entity.getTaskId())
                ? PushNotificationConfigStore.resolveProtocolVersion(null)
                : PushNotificationConfigStore.resolveProtocolVersion(entity.getProtocolVersion());
    }

    /**
     * 获取一个task下全部推送配置的协议版本映射
     * @param taskId A2A任务ID
     * @return Map key=configId，value=解析后的协议版本，保持查询顺序
     */
    @Override
    public Map<String, String> getProtocolVersions(String taskId) {
        Map<String, String> versions = new LinkedHashMap<>();
        mapper.selectList(new LambdaQueryWrapper<A2aPushConfigEntity>()
                        .eq(A2aPushConfigEntity::getTaskId, taskId))
                .forEach(entity -> versions.put(entity.getConfigId(),
                        PushNotificationConfigStore.resolveProtocolVersion(entity.getProtocolVersion())));
        return versions;
    }

    /**
     * 游标分页计算起始下标
     * @param configs 全量内存列表
     * @param pageToken 游标token，对应某一条config的id
     * @return 分页开始索引；token为空返回0；找不到token返回列表末尾（返回空页）
     */
    private int pageStart(List<TaskPushNotificationConfig> configs, String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return 0;
        }
        for (int index = 0; index < configs.size(); index++) {
            if (pageToken.equals(configs.get(index).id())) {
                return index;
            }
        }
        // token不存在，直接跳到列表末尾，返回空结果
        return configs.size();
    }
}
