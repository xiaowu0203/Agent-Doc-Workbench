package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.OnlineEvaluationFeign;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.feign.vo.OnlineRouteVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.task.config.OnlineTaskProperties;
import com.agentdoc.task.mapper.TaskCreationIntentMapper;
import com.agentdoc.task.pojo.entity.TaskCreationIntentEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;

/** 分配前复核 Task 所有的冻结意图，不保存用户令牌，不在数据库事务内 RPC。 */
@Service
@RequiredArgsConstructor
public class TaskOnlineRoutingService {
    private final OnlineTaskProperties properties;
    private final OnlineEvaluationFeign evaluation;
    private final TaskCreationIntentMapper intents;

    public OnlineRouteVO route(TaskCreationIntentEntity intent) {
        if (!properties.isEnabled()) { return new OnlineRouteVO(null, "FEATURE_DISABLED"); }
        return data(evaluation.route(request(intent)));
    }

    public void requireKeyWhenParticipating(TaskEntity prepared) {
        if (!properties.isEnabled()) { return; }
        var request = request(prepared, AuthUtils.getUserIdOrException(), null, null);
        if (Boolean.TRUE.equals(data(evaluation.checkScope(request)))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, OnlineReasonCode.REQUEST_KEY_REQUIRED.name());
        }
    }

    /** 调用方必须再次检查当前 Task 创建权限与文档归属。 */
    public OnlineAssignmentRequestDTO proof(String taskId) {
        return request(proofIntent(taskId));
    }

    public TaskEntity frozenProof(String taskId) { return TaskCreationIntentPersistenceService.frozen(proofIntent(taskId)); }

    private TaskCreationIntentEntity proofIntent(String taskId) {
        long id = OnlineProtocolUtils.id(taskId);
        var intent = intents.selectOne(new LambdaQueryWrapper<TaskCreationIntentEntity>().eq(TaskCreationIntentEntity::getTaskId, id));
        if (intent == null || intent.getInputJson() == null || !Objects.equals(intent.getActorId(), AuthUtils.getUserIdOrException())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, OnlineReasonCode.RESOURCE_FORBIDDEN.name());
        }
        return intent;
    }

    public static OnlineAssignmentRequestDTO request(TaskCreationIntentEntity intent) {
        return request(TaskCreationIntentPersistenceService.frozen(intent), intent.getActorId(), intent.getRequestKey(), intent.getRequestHash());
    }

    private static OnlineAssignmentRequestDTO request(TaskEntity task, Long actor, String key, String hash) {
        return new OnlineAssignmentRequestDTO(task.getId().toString(), task.getSpaceId().toString(), task.getAgentId().toString(),
                task.getDocumentId().toString(), actor.toString(), key, hash, task.getInputSnapshotHash(),
                task.getDocumentVersionSnapshot().toString(), task.getDocumentContentSha256(),
                task.getTokenBudget() == null ? null : task.getTokenBudget().toString(), task.getInputSnapshotSchemaVersion());
    }

    private static <T> T data(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, OnlineReasonCode.ONLINE_FACT_UNKNOWN.name());
        }
        return result.data();
    }
}
