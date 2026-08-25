package com.agentdoc.task.a2a;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.task.convertor.A2aTaskConvertor;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.service.TokenUsageService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.a2aproject.sdk.spec.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A2A任务状态同步服务
 * <p>
 * 负责将远端Agent‑Server返回的任务状态数据同步更新到本地数据库。
 * 使用条件更新，仅当本地任务仍处于远端活跃状态时才允许更新，防止终态被覆盖。
 * 更新成功后，如果任务已完成，记录Token消耗用量；内部调用Agent服务Feign接口获取执行配置档案。
 * 回调链路、定时对账任务均会调用该服务完成状态落地。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class A2aTaskSynchronizationService {

    private final TaskMapper taskMapper;
    private final AgentFeign agentFeign;
    private final TokenUsageService tokenUsageService;

    /**
     * 执行远端任务 → 本地任务实体状态同步并落库
     * <p>
     * 流程：转换器把远端数据写入task实体 → 条件式数据库更新；
     * 更新条件：主键匹配 且 当前本地任务处于远端活跃状态，避免已完结任务被回调/对账覆盖。
     * 更新行数为0代表条件不满足，直接返回false。
     * 任务状态为完成时，调用Token用量服务记录消耗账单。
     * </p>
     *
     * @param task        本地任务实体，会被{@link A2aTaskConvertor#apply(TaskEntity, Task)}回填远端数据
     * @param remoteTask  远端A2A协议任务对象
     * @return true：数据库更新成功；false：条件不满足未执行更新
     * @throws BusinessException 获取Agent执行档案接口调用异常时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean synchronize(TaskEntity task, Task remoteTask) {
        // 将远端A2A任务数据转换、回填到本地task对象
        A2aTaskConvertor.apply(task, remoteTask);
        // 条件更新：仅本地任务还处于远端活跃状态，才允许覆盖状态，保护已终态数据
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, task.getId())
                .in(TaskEntity::getStatus, TaskStatus.remoteActiveCodes())
                .set(TaskEntity::getA2aTaskId, task.getA2aTaskId())
                .set(TaskEntity::getA2aContextId, task.getA2aContextId())
                .set(TaskEntity::getStatus, task.getStatus())
                .set(TaskEntity::getLastHeartbeatAt, task.getLastHeartbeatAt())
                .set(TaskEntity::getEndTime, task.getEndTime())
                .set(TaskEntity::getResultSummary, task.getResultSummary())
                .set(TaskEntity::getErrorMessage, task.getErrorMessage())
                .set(TaskEntity::getTokensUsed, task.getTokensUsed())
                .set(TaskEntity::getTokensEstimated, task.getTokensEstimated())
                .set(TaskEntity::getAgentExecutionId, task.getAgentExecutionId())
                .set(TaskEntity::getPromptHash, task.getPromptHash()));
        // 更新行数为0：说明任务已经不是远端活跃状态，放弃同步
        if (updated == 0) {
            return false;
        }
        // 任务已完成，记录Token消耗统计，同时做任务Token预算管控
        if (TaskStatus.COMPLETED.getCodeEquals(task.getStatus())) {
            tokenUsageService.recordRemote(task, requireProfile(task.getAgentId()),
                    A2aTaskConvertor.tokenUsage(remoteTask));
        }
        return true;
    }

    /**
     * 根据agentId远程获取Agent执行配置档案
     * <p>Feign调用Agent服务，校验返回结果，非成功则抛出业务异常中断同步流程。</p>
     *
     * @param agentId Agent唯一ID
     * @return Agent执行档案VO
     * @throws BusinessException Feign调用返回失败、返回数据为空时抛出
     */
    private AgentExecutionProfileVO requireProfile(Long agentId) {
        // 根据AgentId查询Agent执行档案VO信息
        Result<AgentExecutionProfileVO> result = agentFeign.getExecutionProfile(agentId);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "Agent Service 调用失败" : result.message());
        }
        return result.data();
    }
}
