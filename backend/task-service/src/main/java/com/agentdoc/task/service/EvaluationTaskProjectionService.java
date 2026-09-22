package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.feign.dto.EvaluationDocumentChangePreviewDTO;
import com.agentdoc.common.feign.dto.AgentEvaluationEvidenceQueryDTO;
import com.agentdoc.common.feign.dto.EvaluationTaskBatchQueryDTO;
import com.agentdoc.common.feign.vo.AgentEvaluationEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationArtifactEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangeEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangePreviewVO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.feign.vo.EvaluationTaskCancelVO;
import com.agentdoc.common.feign.vo.EvaluationTaskStatusVO;
import com.agentdoc.common.security.EvaluationWorkerCapabilityVerifier;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.task.a2a.A2aTaskClient;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.enums.ExecutionArtifactType;
import com.agentdoc.task.enums.TaskStatus;
import com.agentdoc.task.mapper.ChangeRequestMapper;
import com.agentdoc.task.mapper.ExecutionArtifactMapper;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.mapper.TokenUsageDetailMapper;
import com.agentdoc.task.mcp.McpChangeProposal;
import com.agentdoc.task.pojo.entity.ChangeRequestEntity;
import com.agentdoc.task.pojo.entity.ExecutionArtifactEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.entity.TokenUsageDetailEntity;
import com.agentdoc.task.security.TaskCapabilityCryptoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * WorkerCapability 保护的任务投影服务
 * <p>
 * 面向评估Worker，提供回放任务状态查询、评估证据打包、批量取消能力；
 * 所有接口受 WorkerCapability JWT 保护，校验RunId、空间、任务ID哈希与操作权限；
 * 仅允许操作ISOLATED模式、REPLAY血缘的回放任务，聚合Agent执行证据、Token账单、产物、变更请求计数。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class EvaluationTaskProjectionService {
    private final TaskMapper taskMapper;
    private final TokenUsageDetailMapper tokenUsageDetailMapper;
    private final ExecutionArtifactMapper artifactMapper;
    private final ChangeRequestMapper changeRequestMapper;
    private final AgentFeign agentFeign;
    private final DocumentFeign documentFeign;
    private final EvaluationWorkerCapabilityVerifier capabilityVerifier;
    private final A2aTaskClient a2aTaskClient;
    private final TaskCapabilityCryptoService cryptoService;

    /**
     * 批量查询评估任务状态
     * @param token WorkerCapability鉴权令牌
     * @param request 批量查询DTO，携带runId、spaceId、taskId列表
     * @return 任务状态VO列表，包含任务ID、状态、终态标记、执行链路信息
     * @throws BusinessException 令牌非法、权限不匹配、任务不在Capability绑定范围内抛出异常
     */
    public List<EvaluationTaskStatusVO> statuses(String token, EvaluationTaskBatchQueryDTO request) {
        List<TaskEntity> tasks = authorize(token, request, JwtConstant.ACTION_BATCH_READ_TASK_STATUS);
        return tasks.stream().map(task -> {
            TaskStatus status = TaskStatus.fromCode(task.getStatus());
            return new EvaluationTaskStatusVO(task.getId(), task.getStatus(), status.name(),
                    isTerminal(status), task.getAgentExecutionId(), task.getTraceId(),
                    task.getStartTime(), task.getEndTime());
        }).toList();
    }

    /**
     * 批量获取评估证据打包数据
     * <p>聚合Agent执行评估事实、Token用量账本、执行产物、变更请求数量，返回打包证据VO供给评估计算。</p>
     * @param token WorkerCapability鉴权令牌
     * @param request 批量查询DTO
     * @return 评估证据BundleVO列表
     * @throws BusinessException 鉴权失败、数据冲突、Agent证据不可用时抛出异常
     */
    public List<EvaluationEvidenceBundleVO> evidence(String token, EvaluationTaskBatchQueryDTO request) {
        List<TaskEntity> tasks = authorize(token, request, JwtConstant.ACTION_READ_EVALUATION_EVIDENCE);
        List<Long> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        // 调用Agent服务获取Agent执行评估证据
        Map<Long, AgentEvaluationEvidenceVO> agentByTask = requireData(agentFeign.queryEvaluationEvidence(
                new AgentEvaluationEvidenceQueryDTO(taskIds))).stream()
                .collect(Collectors.toMap(AgentEvaluationEvidenceVO::taskId, Function.identity(),
                        (left, right) -> {
                            throw new BusinessException(ErrorCode.CONFLICT,
                                    "同一 Task 存在多个 AgentExecution 评估投影");
                        }));
        // 查询Token用量明细账本，一个任务仅允许一条权威记录
        Map<Long, TokenUsageDetailEntity> tokenByTask = tokenUsageDetailMapper.selectList(
                        new LambdaQueryWrapper<TokenUsageDetailEntity>()
                                .in(TokenUsageDetailEntity::getTaskId, taskIds)).stream()
                .collect(Collectors.toMap(TokenUsageDetailEntity::getTaskId, Function.identity(),
                        (left, right) -> {
                            throw new BusinessException(ErrorCode.CONFLICT, "同一 Task 存在多条 Token 权威账本");
                        }));
        // 查询执行产物，按任务分组并保持序列顺序
        Map<Long, List<EvaluationArtifactEvidenceVO>> artifactsByTask = artifactMapper.selectList(
                        new LambdaQueryWrapper<ExecutionArtifactEntity>()
                                .in(ExecutionArtifactEntity::getTaskId, taskIds)
                                .ne(ExecutionArtifactEntity::getArtifactType,
                                        ExecutionArtifactType.RESULT_SUMMARY.name())
                                .orderByAsc(ExecutionArtifactEntity::getTaskId)
                                .orderByAsc(ExecutionArtifactEntity::getSequenceNo)).stream()
                .collect(Collectors.groupingBy(ExecutionArtifactEntity::getTaskId,
                        Collectors.mapping(EvaluationTaskProjectionService::artifactVO, Collectors.toList())));
        // 统计每个任务关联的变更请求数量
        Map<Long, Long> changeRequestCount = changeRequestMapper.selectList(
                        new LambdaQueryWrapper<ChangeRequestEntity>()
                                .in(ChangeRequestEntity::getSourceTaskId, taskIds)).stream()
                .collect(Collectors.groupingBy(ChangeRequestEntity::getSourceTaskId, Collectors.counting()));

        return tasks.stream().map(task -> bundle(task, agentByTask.get(task.getId()),
                tokenByTask.get(task.getId()), artifactsByTask.getOrDefault(task.getId(), List.of()),
                changeRequestCount.getOrDefault(task.getId(), 0L))).toList();
    }

    /**
     * 仅为 document-change-validator 解析候选变更，并通过 Replay Task 自身能力在冻结版本上预览。
     * 返回值不包含 Artifact payload、基准正文或提案正文。
     */
    public List<EvaluationDocumentChangeEvidenceVO> documentChanges(
            String token, EvaluationTaskBatchQueryDTO request) {
        List<TaskEntity> tasks = authorize(token, request, JwtConstant.ACTION_VALIDATE_DOCUMENT_CHANGE);
        List<Long> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        Map<Long, List<ExecutionArtifactEntity>> artifactsByTask = artifactMapper.selectList(
                        new LambdaQueryWrapper<ExecutionArtifactEntity>()
                                .in(ExecutionArtifactEntity::getTaskId, taskIds)
                                .eq(ExecutionArtifactEntity::getArtifactType, ExecutionArtifactType.CHANGE_PROPOSAL.name())
                                .orderByAsc(ExecutionArtifactEntity::getTaskId)
                                .orderByAsc(ExecutionArtifactEntity::getSequenceNo)).stream()
                .collect(Collectors.groupingBy(ExecutionArtifactEntity::getTaskId));
        List<EvaluationDocumentChangeEvidenceVO> result = new ArrayList<>();
        for (TaskEntity task : tasks) {
            for (ExecutionArtifactEntity artifact : artifactsByTask.getOrDefault(task.getId(), List.of())) {
                result.add(validateDocumentChange(task, artifact));
            }
        }
        return List.copyOf(result);
    }

    private EvaluationDocumentChangeEvidenceVO validateDocumentChange(
            TaskEntity task, ExecutionArtifactEntity artifact) {
        McpChangeProposal proposal = JsonUtils.parse(artifact.getPayloadJson(), McpChangeProposal.class);
        if (proposal == null || proposal.baseVersion() == null || proposal.changes() == null
                || !Objects.equals(proposal.baseVersion(), task.getDocumentVersionSnapshot())) {
            return invalidDocumentChange(task, artifact, "ARTIFACT_SCHEMA_INVALID");
        }
        String capability = cryptoService.decrypt(task.getCapabilityToken());
        if (capability == null || capability.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Replay Task Capability 不可用");
        }
        String previous = TaskCapabilityContext.current();
        try {
            TaskCapabilityContext.set(capability);
            Result<EvaluationDocumentChangePreviewVO> response = documentFeign.previewEvaluationDocumentChanges(
                    new EvaluationDocumentChangePreviewDTO(task.getDocumentId(), proposal.baseVersion(),
                            task.getDocumentContentSha256(), proposal.changes()));
            if (response == null || response.code() != ErrorCode.SUCCESS.getCode() || response.data() == null) {
                return invalidDocumentChange(task, artifact, "DOCUMENT_PREVIEW_REJECTED");
            }
            EvaluationDocumentChangePreviewVO preview = response.data();
            return new EvaluationDocumentChangeEvidenceVO(task.getId(), artifact.getId(),
                    artifact.getPayloadSha256(), !preview.conflicted(), preview.conflicted(),
                    preview.proposedContentSha256(), preview.conflicted() ? "DOCUMENT_VERSION_CONFLICT" : null);
        } finally {
            if (previous == null) {
                TaskCapabilityContext.clear();
            } else {
                TaskCapabilityContext.set(previous);
            }
        }
    }

    private static EvaluationDocumentChangeEvidenceVO invalidDocumentChange(
            TaskEntity task, ExecutionArtifactEntity artifact, String failureCode) {
        return new EvaluationDocumentChangeEvidenceVO(task.getId(), artifact.getId(),
                artifact.getPayloadSha256(), false, false, null, failureCode);
    }

    /**
     * 批量发起回放任务取消
     * @param token WorkerCapability鉴权令牌
     * @param request 批量查询DTO
     * @return 每条任务对应的取消结果VO
     */
    public List<EvaluationTaskCancelVO> cancel(String token, EvaluationTaskBatchQueryDTO request) {
        List<TaskEntity> tasks = authorize(token, request, JwtConstant.ACTION_CANCEL_RUN_TASKS);
        List<EvaluationTaskCancelVO> results = new ArrayList<>(tasks.size());
        for (TaskEntity task : tasks) {
            results.add(cancelOne(task));
        }
        return List.copyOf(results);
    }

    /**
     * 取消单个回放任务，处理不同状态分支
     * <ul>
     *     <li>已终态：直接返回成功，不做操作</li>
     *     <li>正在取消中：返回已待取消</li>
     *     <li>不可取消状态：返回失败</li>
     *     <li>PENDING：直接置为TERMINATED</li>
     *     <li>其他可终止状态：先更新本地为CANCELING，再调用A2A远端取消</li>
     * </ul>
     * @param task 待取消任务实体
     * @return 取消结果VO，包含成功标记、状态、错误码
     */
    private EvaluationTaskCancelVO cancelOne(TaskEntity task) {
        TaskStatus status = TaskStatus.fromCode(task.getStatus());
        if (isTerminal(status)) {
            return new EvaluationTaskCancelVO(task.getId(), true, status.name(), "ALREADY_TERMINAL");
        }
        if (status == TaskStatus.CANCELING) {
            return new EvaluationTaskCancelVO(task.getId(), true, status.name(), "CANCEL_ALREADY_PENDING");
        }
        if (!status.canTerminate()) {
            return new EvaluationTaskCancelVO(task.getId(), false, status.name(), "STATUS_NOT_CANCELABLE");
        }
        if (status == TaskStatus.PENDING) {
            int updated = taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                    .eq(TaskEntity::getId, task.getId())
                    .eq(TaskEntity::getStatus, status.getCode())
                    .set(TaskEntity::getStatus, TaskStatus.TERMINATED.getCode())
                    .set(TaskEntity::getErrorMessage, "EvaluationRun 请求取消")
                    .set(TaskEntity::getEndTime, LocalDateTime.now()));
            return new EvaluationTaskCancelVO(task.getId(), updated == 1,
                    updated == 1 ? TaskStatus.TERMINATED.name() : status.name(),
                    updated == 1 ? null : "CONCURRENT_STATUS_CHANGE");
        }
        int updated = taskMapper.update(null, new LambdaUpdateWrapper<TaskEntity>()
                .eq(TaskEntity::getId, task.getId())
                .eq(TaskEntity::getStatus, status.getCode())
                .set(TaskEntity::getStatus, TaskStatus.CANCELING.getCode()));
        if (updated == 0) {
            return new EvaluationTaskCancelVO(task.getId(), false, status.name(), "CONCURRENT_STATUS_CHANGE");
        }
        if (task.getA2aTaskId() == null) {
            return new EvaluationTaskCancelVO(task.getId(), true, TaskStatus.CANCELING.name(), null);
        }
        try {
            a2aTaskClient.cancel(task.getA2aTaskId(), cryptoService.decrypt(task.getCapabilityToken()));
            return new EvaluationTaskCancelVO(task.getId(), true, TaskStatus.CANCELING.name(), null);
        } catch (RuntimeException exception) {
            return new EvaluationTaskCancelVO(task.getId(), false, TaskStatus.CANCELING.name(),
                    "REMOTE_CANCEL_UNAVAILABLE");
        }
    }

    /**
     * 校验WorkerCapability令牌，校验RunId、空间、任务哈希、操作权限，并且校验任务必须为REPLAY+ISOLATED模式
     * @param token WorkerCapability JWT
     * @param request 批量请求DTO
     * @param action 当前请求要执行的动作编码
     * @return 校验通过的任务实体列表，保持入参taskIds顺序
     * @throws BusinessException 令牌无效、权限不匹配、任务集合哈希不一致、任务属性不满足回放约束
     */
    private List<TaskEntity> authorize(String token, EvaluationTaskBatchQueryDTO request, String action) {
        List<Long> taskIds = validateRequest(request);
        Jwt jwt;
        try {
            jwt = capabilityVerifier.verify(token);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Evaluation WorkerCapability 无效或已过期");
        }
        if (!Objects.equals(request.runId(), numberClaim(jwt, JwtConstant.CLAIM_RUN_ID))
                || !Objects.equals(request.spaceId(), numberClaim(jwt, JwtConstant.CLAIM_SPACE_ID))
                || jwt.getClaimAsStringList(JwtConstant.CLAIM_WORKER_ACTIONS) == null
                || !jwt.getClaimAsStringList(JwtConstant.CLAIM_WORKER_ACTIONS).contains(action)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Evaluation WorkerCapability 不允许该操作");
        }
        String expectedHash = StableSnapshotUtils.snapshotHash(1, taskIds);
        if (!expectedHash.equals(jwt.getClaimAsString(JwtConstant.CLAIM_TASK_IDS_HASH))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Evaluation WorkerCapability 的 Task 集合不匹配");
        }
        List<TaskEntity> tasks = taskMapper.selectBatchIds(taskIds);
        Map<Long, TaskEntity> byId = tasks.stream().collect(Collectors.toMap(TaskEntity::getId, Function.identity()));
        if (tasks.size() != taskIds.size() || tasks.stream().anyMatch(task ->
                !request.spaceId().equals(task.getSpaceId())
                        || !TaskLineageType.REPLAY.name().equals(task.getLineageType())
                        || !TaskExecutionMode.ISOLATED.name().equals(task.getExecutionMode()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "WorkerCapability 绑定的 Replay Task 已失效");
        }
        return taskIds.stream().map(byId::get).toList();
    }

    /**
     * 校验批量请求参数，清洗并返回合法任务ID列表
     * @param request 批量查询DTO
     * @return 去重、排序后的有效任务ID列表
     * @throws BusinessException 参数缺失、ID非法、数量超出批次上限
     */
    private static List<Long> validateRequest(EvaluationTaskBatchQueryDTO request) {
        if (request == null || request.runId() == null || request.runId() <= 0
                || request.spaceId() == null || request.spaceId() <= 0 || request.taskIds() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Evaluation Task 批量请求参数不完整");
        }
        List<Long> taskIds = request.taskIds().stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (taskIds.isEmpty() || taskIds.size() > TaskConstant.MAX_REPLAY_BATCH_SIZE
                || taskIds.size() != request.taskIds().size() || taskIds.stream().anyMatch(id -> id <= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Task ID 集合必须包含 1~100 个不重复有效 ID");
        }
        return taskIds;
    }

    /**
     * 将任务、Agent证据、Token账单、产物、变更请求计数组装成EvaluationEvidenceBundleVO
     * @param task 任务实体
     * @param agent Agent评估证据VO
     * @param token Token用量明细
     * @param artifacts 执行产物VO列表
     * @param changeRequestCount 关联变更请求数量
     * @return 打包后的评估证据Bundle
     */
    private static EvaluationEvidenceBundleVO bundle(
            TaskEntity task, AgentEvaluationEvidenceVO agent, TokenUsageDetailEntity token,
            List<EvaluationArtifactEvidenceVO> artifacts, long changeRequestCount) {
        TaskStatus taskStatus = TaskStatus.fromCode(task.getStatus());
        return new EvaluationEvidenceBundleVO(task.getId(), task.getSpaceId(), task.getStatus(), taskStatus.name(),
                task.getResultSummary(), agent == null ? task.getAgentExecutionId() : agent.executionId(),
                agent == null ? null : agent.status(), agent == null ? task.getTraceId() : agent.traceId(),
                agent == null ? null : agent.spanId(), agent == null ? task.getStartTime() : agent.startedAt(),
                agent == null ? task.getEndTime() : agent.finishedAt(), token == null ? null : token.getInputTokens(),
                token == null ? null : token.getCachedInputTokens(), token == null ? null : token.getOutputTokens(),
                token == null ? null : token.getEstimatedCost(), token == null ? null : token.getCurrency(),
                task.getRetryCount(), agent == null ? 0 : agent.toolCallCount(),
                agent == null ? 0 : agent.failedToolCallCount(),
                agent == null ? 0 : agent.externalMcpCallCount(), changeRequestCount, artifacts);
    }

    /**
     * 将执行产物实体转为评估产物VO
     * @param artifact 执行产物数据库实体
     * @return EvaluationArtifactEvidenceVO
     */
    private static EvaluationArtifactEvidenceVO artifactVO(ExecutionArtifactEntity artifact) {
        return new EvaluationArtifactEvidenceVO(artifact.getId(), artifact.getSequenceNo(),
                artifact.getArtifactType(), artifact.getSchemaVersion(), artifact.getPayloadSha256(),
                artifact.getSourceToolCallId());
    }

    /**
     * 从Jwt声明中读取数值类型字段，转为Long
     * @param jwt jwt对象
     * @param name 声明名称
     * @return 数值，非数字返回null
     */
    private static Long numberClaim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * 判断任务状态是否为终态：完成 / 失败 / 终止
     * @param status 任务状态枚举
     * @return true=终态
     */
    private static boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || status == TaskStatus.TERMINATED;
    }

    /**
     * 校验Feign远程调用返回结果，成功且data非空才返回数据，否则抛业务异常
     * @param result 远程返回Result包装
     * @return 响应data
     * @param <T> data类型
     */
    private static <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent 执行评估事实暂不可用");
        }
        return result.data();
    }
}
