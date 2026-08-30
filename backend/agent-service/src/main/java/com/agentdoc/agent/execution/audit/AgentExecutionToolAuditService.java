package com.agentdoc.agent.execution.audit;

import com.agentdoc.agent.enums.ToolCallAuditStatus;
import com.agentdoc.agent.mapper.AgentExecutionToolCallMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Agent【工具调用】审计服务
 * <p>
 * 负责Agent流程中每一次工具/MCP调用的审计记录生命周期管理。
 * 与 {@link AgentExecutionModelCallAuditService} 成对：一个审计LLM模型请求，一个审计工具调用。
 * <p>
 * 事务设计：全部使用 {@link Propagation#REQUIRES_NEW} 独立新事务。
 * 即使上层Agent主业务事务发生回滚，工具调用审计记录仍然可以持久入库，保证调用痕迹不丢失，用于问题排查、调用溯源、统计对账。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AgentExecutionToolAuditService {
    private final AgentExecutionToolCallMapper mapper;

    /**
     * 创建并启动一条工具调用审计记录
     *
     * @param executionId    Agent执行实例ID，同一次Agent任务的唯一标识
     * @param sequence       工具调用序号，一次Agent执行内多轮工具调用的递增序列号
     * @param toolName       工具名称
     * @param source         工具来源类型（内置工具 / MCP等）
     * @param sourceKey      来源唯一key，例如MCP serverKey
     * @param mcpServerId    MCP服务ID，如果不是MCP工具则为null
     * @param argumentsHash  工具入参参数SHA‑256摘要
     * @param argumentsSize  工具入参参数字节大小
     * @return 已持久化的工具调用审计实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentExecutionToolCallEntity start(Long executionId, int sequence, String toolName, String source,
                                              String sourceKey, Long mcpServerId,
                                              String argumentsHash, long argumentsSize) {
        AgentExecutionToolCallEntity entity = new AgentExecutionToolCallEntity();
        entity.setExecutionId(executionId);
        entity.setSequenceNo(sequence);
        entity.setToolName(toolName);
        entity.setToolSource(source);
        entity.setToolSourceKey(sourceKey);
        entity.setMcpServerId(mcpServerId);
        entity.setArgumentsSha256(argumentsHash);
        entity.setArgumentsSize(argumentsSize);
        entity.setStatus(ToolCallAuditStatus.STARTED.name());
        entity.setStartedAt(LocalDateTime.now());
        // 强校验插入必须生效1行，否则抛出异常
        requireInserted(mapper.insert(entity));
        return entity;
    }

    /**
     * 标记工具调用审计为成功结束
     * <p>
     * 更新状态为SUCCEEDED，写入返回结果摘要、结果字节大小与完成时间。
     *
     * @param entity      工具调用审计实体
     * @param resultHash  工具返回结果SHA‑256摘要
     * @param resultSize  工具返回结果字节大小
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(AgentExecutionToolCallEntity entity, String resultHash, long resultSize) {
        entity.setResultSha256(resultHash);
        entity.setResultSize(resultSize);
        entity.setStatus(ToolCallAuditStatus.SUCCEEDED.name());
        entity.setFinishedAt(LocalDateTime.now());
        requireUpdated(mapper.updateById(entity));
    }

    /**
     * 标记工具调用审计为失败结束
     *
     * @param entity    工具调用审计实体
     * @param errorType 错误类型编码，用于归类统计失败场景
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(AgentExecutionToolCallEntity entity, String errorType) {
        entity.setStatus(ToolCallAuditStatus.FAILED.name());
        entity.setErrorType(errorType);
        entity.setFinishedAt(LocalDateTime.now());
        requireUpdated(mapper.updateById(entity));
    }

    /**
     * 批量补偿僵死未结束的工具调用审计记录
     * <p>
     * 由定时任务 {@link AgentExecutionAuditReconcileJob} 调度调用。
     * 服务崩溃、线程异常中断会导致部分记录永久停留在STARTED状态；
     * 将开始时间早于cutoff阈值且状态为STARTED的记录强制置为失败，错误标记 AUIDT_FINALIZATION_MISSING。
     * 直接执行数据库批量update，不加载实体到内存，保证大数据量下性能。
     *
     * @param cutoff 时间阈值，早于此时间点仍处于STARTED视为僵死任务
     * @return 本次批量更新处理的记录行数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int failStaleStarted(LocalDateTime cutoff) {
        LocalDateTime finishedAt = LocalDateTime.now();
        LambdaUpdateWrapper<AgentExecutionToolCallEntity> wrapper = new LambdaUpdateWrapper<AgentExecutionToolCallEntity>()
                .eq(AgentExecutionToolCallEntity::getStatus, ToolCallAuditStatus.STARTED.name())
                .lt(AgentExecutionToolCallEntity::getStartedAt, cutoff)
                .set(AgentExecutionToolCallEntity::getStatus, ToolCallAuditStatus.FAILED.name())
                .set(AgentExecutionToolCallEntity::getErrorType, "AUDIT_FINALIZATION_MISSING")
                .set(AgentExecutionToolCallEntity::getFinishedAt, finishedAt);
        return mapper.update(null, wrapper);
    }

    /**
     * 校验单条更新影响行数，必须更新1行。
     * 防止记录不存在、已被并发修改删除，审计状态静默丢失，快速失败抛出异常。
     *
     * @param rows mybatis‑plus update返回影响行数
     */
    private void requireUpdated(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("工具调用审计状态更新失败");
        }
    }

    /**
     * 校验插入影响行数，必须插入1行，否则抛出异常。
     *
     * @param rows mybatis‑plus insert返回影响行数
     */
    private void requireInserted(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("工具调用审计记录创建失败");
        }
    }
}
