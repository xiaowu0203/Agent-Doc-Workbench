package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.mapper.AgentExecutionModelCallMapper;
import com.agentdoc.agent.mapper.AgentExecutionToolCallMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionModelCallEntity;
import com.agentdoc.agent.pojo.entity.AgentExecutionToolCallEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.dto.AgentExecutionTokenUsageBatchQueryDTO;
import com.agentdoc.common.feign.dto.AgentToolUsageQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageBatchVO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.common.feign.vo.AgentToolSourceCountVO;
import com.agentdoc.common.feign.vo.AgentToolUsageStatsVO;
import com.agentdoc.common.utils.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.USAGE_READ;

/**
 * Agent 执行审计只读查询服务，只向工作台返回脱敏后的展示投影。
 */
@Service
@RequiredArgsConstructor
public class AgentExecutionQueryService {

    private final AgentExecutionMapper executionMapper;
    private final AgentExecutionModelCallMapper modelCallMapper;
    private final AgentExecutionToolCallMapper toolCallMapper;
    private final SpaceAccessService spaceAccessService;

    /**
     * 按工作台任务查询一次执行及其模型、工具调用审计。
     *
     * @param taskId 工作台任务 ID
     * @param spaceId 路由空间 ID
     * @return 尚未生成 Agent 执行记录时返回 null
     */
    public AgentExecutionAuditVO getByWorkbenchTask(Long taskId, Long spaceId) {
        spaceAccessService.requirePermission(spaceId, TASK_READ);
        AgentExecutionEntity execution = executionMapper.selectOne(
                new LambdaQueryWrapper<AgentExecutionEntity>()
                        .eq(AgentExecutionEntity::getWorkbenchTaskId, taskId));
        if (execution == null) {
            return null;
        }
        if (!spaceId.equals(execution.getSpaceId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Agent 执行记录不存在");
        }
        List<AgentExecutionModelCallEntity> modelCalls = modelCallMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionModelCallEntity>()
                        .eq(AgentExecutionModelCallEntity::getExecutionId, execution.getId())
                        .orderByAsc(AgentExecutionModelCallEntity::getSequenceNo));
        List<AgentExecutionToolCallEntity> toolCalls = toolCallMapper.selectList(
                new LambdaQueryWrapper<AgentExecutionToolCallEntity>()
                        .eq(AgentExecutionToolCallEntity::getExecutionId, execution.getId())
                        .orderByAsc(AgentExecutionToolCallEntity::getSequenceNo));
        return toVO(execution, modelCalls, toolCalls);
    }

    /**
     * 提供给任务同步链路的最小 Token 投影，避免内部回调依赖用户权限审计接口。
     */
    public AgentExecutionTokenUsageVO getTokenUsageByWorkbenchTask(Long taskId) {
        AgentExecutionEntity execution = executionMapper.selectOne(
                new LambdaQueryWrapper<AgentExecutionEntity>()
                        .eq(AgentExecutionEntity::getWorkbenchTaskId, taskId));
        if (execution == null) {
            return null;
        }
        return new AgentExecutionTokenUsageVO(execution.getInputTokens(), execution.getInputTokensEstimated(),
                execution.getCachedInputTokens(), execution.getCachedInputTokensEstimated(),
                execution.getOutputTokens(), execution.getOutputTokensEstimated());
    }

    /**
     * 批量提供版本历史所需的最小 Token 投影，避免按版本逐条调用执行服务。
     */
    public List<AgentExecutionTokenUsageBatchVO> getTokenUsagesByWorkbenchTasks(
            AgentExecutionTokenUsageBatchQueryDTO request) {
        List<Long> taskIds = request == null || request.taskIds() == null ? List.of()
                : request.taskIds().stream().filter(Objects::nonNull).distinct().toList();
        if (taskIds.isEmpty()) {
            return List.of();
        }
        if (taskIds.size() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次最多查询 100 个任务的 Token 用量");
        }
        return executionMapper.selectList(new LambdaQueryWrapper<AgentExecutionEntity>()
                        .in(AgentExecutionEntity::getWorkbenchTaskId, taskIds)).stream()
                .map(execution -> new AgentExecutionTokenUsageBatchVO(
                        execution.getWorkbenchTaskId(), execution.getInputTokens(),
                        execution.getInputTokensEstimated(), execution.getCachedInputTokens(),
                        execution.getCachedInputTokensEstimated(), execution.getOutputTokens(),
                        execution.getOutputTokensEstimated()))
                .toList();
    }

    /**
     * 查询空间在指定时间范围内的工具来源分布。
     */
    public AgentToolUsageStatsVO getToolUsageStats(AgentToolUsageQueryDTO query) {
        if (query == null || query.spaceId() == null || query.startAt() == null || query.endAt() == null
                || !query.startAt().isBefore(query.endAt())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工具用量查询条件不合法");
        }
        spaceAccessService.requirePermission(query.spaceId(), USAGE_READ);
        List<AgentToolSourceCountVO> sources = toolCallMapper.aggregateBySource(query).stream()
                .map(row -> new AgentToolSourceCountVO(row.source(), row.calls() == null ? 0 : row.calls()))
                .toList();
        return new AgentToolUsageStatsVO(
                sources.stream().mapToLong(AgentToolSourceCountVO::calls).sum(), sources);
    }

    private AgentExecutionAuditVO toVO(AgentExecutionEntity execution,
                                       List<AgentExecutionModelCallEntity> modelCalls,
                                       List<AgentExecutionToolCallEntity> toolCalls) {
        return new AgentExecutionAuditVO(
                execution.getId(), execution.getWorkbenchTaskId(), execution.getSpaceId(),
                execution.getAgentId(), execution.getAgentNameSnapshot(), execution.getAgentConfigVersion(),
                execution.getMaxIterations(), execution.getExecutionTimeoutSeconds(), execution.getStatus(),
                execution.getCancelRequested(), execution.getPromptHash(), execution.getExecutionSnapshotHash(),
                modelSnapshot(execution), skillSnapshot(execution), toolDefinitions(execution),
                externalMcps(execution), modelCalls.stream().map(this::modelCall).toList(),
                toolCalls.stream().map(this::toolCall).toList(), execution.getInputTokens(),
                execution.getInputTokensEstimated(), execution.getCachedInputTokens(),
                execution.getCachedInputTokensEstimated(), execution.getOutputTokens(),
                execution.getOutputTokensEstimated(), execution.getStartedAt(), execution.getFinishedAt(),
                execution.getCreatedAt());
    }

    private AgentExecutionAuditVO.ModelSnapshot modelSnapshot(AgentExecutionEntity execution) {
        Map<String, Object> model = map(execution.getModelSnapshot());
        return new AgentExecutionAuditVO.ModelSnapshot(
                longValue(model.get("id")), stringValue(model.get("modelKey")),
                execution.getModelDisplayNameSnapshot(), execution.getModelConfigVersion());
    }

    private AgentExecutionAuditVO.SkillSnapshot skillSnapshot(AgentExecutionEntity execution) {
        Map<String, Object> router = map(execution.getSkillRouterSnapshotJson());
        List<AgentExecutionAuditVO.BoundSkill> bound = listMap(execution.getSkillSnapshotJson()).stream()
                .map(value -> new AgentExecutionAuditVO.BoundSkill(
                        longValue(value.get("skillId")), longValue(value.get("skillVersionId")),
                        integerValue(value.get("versionNo")), stringValue(value.get("name")),
                        stringValue(value.get("activationDescription")), stringValue(value.get("sha256"))))
                .toList();
        List<Long> selected = JsonUtils.parse(execution.getSelectedSkillVersionIdsJson(),
                new TypeReference<List<Long>>() { });
        return new AgentExecutionAuditVO.SkillSnapshot(
                execution.getSkillSelectionMode(), execution.getSkillSelectionEffectiveMode(),
                execution.getSkillInstructionHash(), execution.getSkillRouterModelId(),
                longValue(router.get("durationMs")), stringValue(router.get("fallbackReason")),
                stringValue(router.get("inputSha256")), stringValue(router.get("responseSha256")),
                bound, selected == null ? List.of() : selected);
    }

    private List<AgentExecutionAuditVO.ToolDefinitionSnapshot> toolDefinitions(AgentExecutionEntity execution) {
        return listMap(execution.getToolDefinitionSnapshotJson()).stream()
                .map(value -> new AgentExecutionAuditVO.ToolDefinitionSnapshot(
                        stringValue(value.get("name")), stringValue(value.get("source")),
                        stringValue(value.get("sourceKey")), longValue(value.get("mcpServerId"))))
                .toList();
    }

    private List<AgentExecutionAuditVO.ExternalMcpSnapshot> externalMcps(AgentExecutionEntity execution) {
        return listMap(execution.getExternalMcpSnapshotJson()).stream()
                .map(value -> new AgentExecutionAuditVO.ExternalMcpSnapshot(
                        longValue(value.get("serverId")), stringValue(value.get("serverKey")),
                        longValue(value.get("configVersion")), stringValue(value.get("endpointSha256")),
                        stringValue(value.get("authType")), stringList(value.get("toolWhitelist"))))
                .toList();
    }

    private AgentExecutionAuditVO.ModelCall modelCall(AgentExecutionModelCallEntity call) {
        return new AgentExecutionAuditVO.ModelCall(
                call.getSequenceNo(), call.getModelId(), call.getModelConfigVersion(), call.getModelKey(),
                call.getMaxOutputTokens(), call.getTemperature(), call.getStreaming(), call.getMessagesSha256(),
                call.getMessagesSize(), call.getResponseSha256(), call.getResponseSize(), call.getStatus(),
                call.getErrorType(), call.getStartedAt(), call.getFinishedAt());
    }

    private AgentExecutionAuditVO.ToolCall toolCall(AgentExecutionToolCallEntity call) {
        return new AgentExecutionAuditVO.ToolCall(
                call.getSequenceNo(), call.getToolName(), call.getToolSource(), call.getToolSourceKey(),
                call.getMcpServerId(), call.getSkillVersionId(), call.getArgumentsSha256(), call.getArgumentsSize(),
                call.getResultSha256(), call.getResultSize(), call.getStatus(), call.getErrorType(),
                call.getStartedAt(), call.getFinishedAt());
    }

    private Map<String, Object> map(String json) {
        Map<String, Object> value = JsonUtils.parse(json, new TypeReference<Map<String, Object>>() { });
        return value == null ? Map.of() : value;
    }

    private List<Map<String, Object>> listMap(String json) {
        List<Map<String, Object>> value = JsonUtils.parse(json,
                new TypeReference<List<Map<String, Object>>>() { });
        return value == null ? List.of() : value;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
