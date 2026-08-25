package com.agentdoc.agent.a2a.store;

import com.agentdoc.agent.mapper.A2aTaskStoreMapper;
import com.agentdoc.agent.pojo.entity.A2aTaskStoreEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.TaskStateProvider;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Task;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * MySQL实现A2A任务存储，同时实现 {@link TaskStore}、{@link TaskStateProvider} 两个接口
 * <p>
 * 负责A2A Task任务对象持久化，表 a2a_task_store；
 * 完整Task领域对象经过 {@link A2aStorePayloadCodec} 加密后存入 encryptedPayload 大字段；
 * 同时把任务状态、时间戳冗余存表，无需解密即可快速查询任务状态；
 * list列表查询：数据库加载全部符合条件任务到内存InMemoryTaskStore，复用内存实现的分页、过滤逻辑。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class MySqlA2aTaskStore implements TaskStore, TaskStateProvider {

    private final A2aTaskStoreMapper mapper;
    /** 载荷编解码器：Java Task对象 ↔ 加密JSON字符串 */
    private final A2aStorePayloadCodec codec;

    /**
     * 保存/更新A2A任务
     * @param task A2A任务领域对象
     * @param saveHistory 是否保存历史记录（当前MySQL实现暂未处理该参数，仅upsert最新任务快照）
     */
    @Override
    public void save(Task task, boolean saveHistory) {
        A2aTaskStoreEntity entity = new A2aTaskStoreEntity();
        entity.setTaskId(task.id());
        entity.setContextId(task.contextId());
        // 冗余存储任务状态枚举名称，不需要解密payload即可查询状态
        entity.setState(task.status() == null || task.status().state() == null
                ? null : task.status().state().name());
        // 状态变更时间戳，转UTC本地时间存入数据库
        entity.setStatusTimestamp(task.status() == null || task.status().timestamp() == null
                ? null : LocalDateTime.ofInstant(task.status().timestamp().toInstant(), ZoneOffset.UTC));
        // 完整Task对象加密存入大字段
        entity.setEncryptedPayload(codec.encode(task));
        // 幂等upsert：存在更新，不存在插入
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.upsert(entity);
    }

    /**
     * 根据taskId读取完整任务对象
     * @param taskId A2A任务ID
     * @return 任务对象；数据库无记录返回null
     */
    @Override
    public Task get(String taskId) {
        A2aTaskStoreEntity entity = mapper.selectById(taskId);
        return entity == null ? null : codec.decode(entity.getEncryptedPayload(), Task.class);
    }

    /**
     * 删除指定任务
     * @param taskId A2A任务ID
     */
    @Override
    public void delete(String taskId) {
        mapper.deleteById(taskId);
    }

    /**
     * 任务列表查询
     * <p>实现方案：DB查询contextId下全部任务，解密后全部加载到内存InMemoryTaskStore，
     * 直接复用内存版list的过滤、游标分页逻辑，不在MySQL写复杂分页条件。</p>
     * @param params 列表查询参数，包含contextId、pageToken、过滤条件
     * @param context A2A调用上下文
     * @return A2A标准分页任务列表结果
     */
    @Override
    public ListTasksResult list(ListTasksParams params, ServerCallContext context) {
        // 临时内存存储，复用内存版的list实现逻辑
        InMemoryTaskStore delegate = new InMemoryTaskStore();
        mapper.selectList(new LambdaQueryWrapper<A2aTaskStoreEntity>()
                        // 条件：params.contextId不为null时，按contextId过滤
                        .eq(params.contextId() != null, A2aTaskStoreEntity::getContextId, params.contextId()))
                .stream()
                .map(entity -> codec.decode(entity.getEncryptedPayload(), Task.class))
                .forEach(task -> delegate.save(task, true));
        // 委托内存实现做过滤、游标分页
        return delegate.list(params, context);
    }

    /**
     * 判断任务是否活跃（未进入终态，还可以继续执行/取消）
     * @param taskId A2A任务ID
     * @return true：任务运行中/待处理；false：已结束
     */
    @Override
    public boolean isTaskActive(String taskId) {
        Task task = get(taskId);
        return task != null && (task.status() == null || task.status().state() == null
                || !task.status().state().isFinal());
    }

    /**
     * 判断任务是否已经到达终态（完成、失败、取消，不再变更）
     * @param taskId A2A任务ID
     * @return true：任务已终态；false：运行中或不存在
     */
    @Override
    public boolean isTaskFinalized(String taskId) {
        Task task = get(taskId);
        return task != null && task.status() != null && task.status().state() != null
                && task.status().state().isFinal();
    }
}
