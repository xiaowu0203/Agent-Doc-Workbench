package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.dto.AgentBatchQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.task.enums.ChangeRequestStatus;
import com.agentdoc.task.enums.TaskOutputType;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.vo.TaskExecutionDetailVO;
import com.agentdoc.task.pojo.vo.TaskOutputVO;
import com.agentdoc.task.pojo.vo.TaskVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聚合任务基础信息、Agent 执行审计和业务产物的只读查询服务。
 */
@Service
@RequiredArgsConstructor
public class TaskExecutionQueryService {

    private final TaskService taskService;
    private final AgentFeign agentFeign;
    private final ChangeRequestMapper changeRequestMapper;

    /**
     * 查询前端任务执行详情。任务读取权限由 {@link TaskService#detail(Long)} 统一校验。
     *
     * @param taskId 任务 ID
     * @return 聚合执行详情
     */
    public TaskExecutionDetailVO detail(Long taskId) {
        TaskVO task = taskService.detail(taskId);
        AgentExecutionAuditVO execution = executionAudit(taskId, task.spaceId());
        if (execution != null && task.tokensUsed() == null
                && execution.inputTokens() != null && execution.outputTokens() != null) {
            task = task.withTokenUsage(execution.inputTokens() + execution.outputTokens(),
                    Boolean.TRUE.equals(task.tokensEstimated())
                            || Boolean.TRUE.equals(execution.inputTokensEstimated())
                            || Boolean.TRUE.equals(execution.outputTokensEstimated()));
        }
        String agentName = execution == null || execution.agentName() == null
                ? currentAgentName(task.agentId()) : execution.agentName();
        return new TaskExecutionDetailVO(task, agentName,
                Boolean.TRUE.equals(task.tokensEstimated()), execution, output(task, execution));
    }

    private AgentExecutionAuditVO executionAudit(Long taskId, Long spaceId) {
        Result<AgentExecutionAuditVO> result = agentFeign.getExecutionAudit(taskId, spaceId);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "Agent 执行审计查询失败" : result.message());
        }
        return result.data();
    }

    private String currentAgentName(Long agentId) {
        Result<List<AgentRefVO>> result = agentFeign.queryAgentRefs(new AgentBatchQueryDTO(List.of(agentId)));
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "Agent 信息查询失败" : result.message());
        }
        return result.data() == null || result.data().isEmpty() ? null : result.data().getFirst().name();
    }

    private TaskOutputVO output(TaskVO task, AgentExecutionAuditVO execution) {
        ChangeRequestEntity request = changeRequestMapper.selectOne(
                new LambdaQueryWrapper<ChangeRequestEntity>()
                .eq(ChangeRequestEntity::getSourceTaskId, task.id())
                        .orderByDesc(ChangeRequestEntity::getCreatedAt)
                        .last("LIMIT 1"));
        if (request != null) {
            return new TaskOutputVO(TaskOutputType.CHANGE_REQUEST, request.getId(),
                    ChangeRequestStatus.fromCode(request.getStatus()).name(), request.getDocumentId());
        }
        boolean draftWriteSucceeded = execution != null && execution.toolCalls().stream()
                .anyMatch(call -> "workbench_apply_draft_changes".equals(call.toolName())
                        && "SUCCEEDED".equals(call.status()));
        if (task.documentType() == DocType.DRAFT && (task.resultSummary() != null || draftWriteSucceeded)) {
            return new TaskOutputVO(TaskOutputType.DRAFT_DOCUMENT, task.documentId(), null,
                    task.documentId());
        }
        return null;
    }
}
