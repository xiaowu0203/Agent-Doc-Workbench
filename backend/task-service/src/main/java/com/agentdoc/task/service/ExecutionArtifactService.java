package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.JwtConstant;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AgentFeign;
import com.agentdoc.common.feign.dto.ExecutionArtifactAppendDTO;
import com.agentdoc.common.feign.vo.AgentExecutionReplayIdentityVO;
import com.agentdoc.common.feign.vo.ExecutionArtifactAppendVO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.task.constant.TaskConstant;
import com.agentdoc.task.enums.ExecutionArtifactType;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.task.enums.TaskLineageType;
import com.agentdoc.task.mapper.ExecutionArtifactMapper;
import com.agentdoc.task.pojo.entity.ExecutionArtifactEntity;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.pojo.vo.ExecutionArtifactVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 隔离执行不可变候选产物服务
 * <p>
 * 仅支持REPLAY隔离回放任务的产物写入；产物按executionId+sequenceNo唯一约束，具备幂等性；
 * RESULT_SUMMARY结果摘要只能由任务终态回调生成，不允许外部直接追加；存储时校验payload哈希保证内容不可篡改。
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionArtifactService {

    private final ExecutionArtifactMapper artifactMapper;
    private final TaskService taskService;
    private final AgentFeign agentFeign;

    /**
     * 追加隔离执行产物
     * <p>相同 executionId + sequenceNo 仅在hash一致时保证幂等写入；RESULT_SUMMARY类型禁止通过此接口提交。</p>
     * @param taskId 任务ID
     * @param capability 任务能力JWT令牌，用于鉴权
     * @param request 产物追加DTO
     * @return 产物追加结果VO，包含主键、产物类型与payload哈希
     * @throws BusinessException 权限校验失败、非回放隔离任务、产物类型非法、hash不匹配、超出数量限制等抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecutionArtifactAppendVO append(Long taskId, String capability, ExecutionArtifactAppendDTO request) {
        taskService.checkCapability(taskId, capability, JwtConstant.ACTION_CAPTURE_EXECUTION_ARTIFACT);
        TaskEntity task = taskService.require(taskId);
        requireIsolatedReplay(task);
        validateRequest(task, request);
        if (ExecutionArtifactType.RESULT_SUMMARY.name().equals(request.artifactType())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "RESULT_SUMMARY 只能由任务终态同步生成");
        }
        return persist(request, task);
    }

    /**
     * 在隔离任务完成回调的同一事务内保存结果摘要产物RESULT_SUMMARY
     * @param task 隔离回放任务实体
     * @return 产物追加结果VO
     * @throws BusinessException 非回放隔离任务、缺少AgentExecutionId、校验不通过时抛出异常
     */
    public ExecutionArtifactAppendVO appendResultSummary(TaskEntity task) {
        requireIsolatedReplay(task);
        if (task.getAgentExecutionId() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "隔离任务完成时缺少 AgentExecution ID");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", task.getResultSummary());
        String payloadJson = JsonUtils.toJson(payload);
        JsonNode payloadNode = JsonUtils.parse(payloadJson, JsonNode.class);
        int schemaVersion = 1;
        ExecutionArtifactAppendDTO request = new ExecutionArtifactAppendDTO(
                task.getAgentExecutionId(), task.getParentTaskId(),
                TaskConstant.RESULT_SUMMARY_ARTIFACT_SEQUENCE, null,
                ExecutionArtifactType.RESULT_SUMMARY.name(), schemaVersion, payloadJson,
                StableSnapshotUtils.snapshotHash(schemaVersion, payloadNode));
        validateRequest(task, request);
        return persist(request, task);
    }

    /**
     * 持久化产物，处理幂等与并发插入冲突
     * @param request 产物追加DTO
     * @param task 任务实体
     * @return 产物追加VO
     */
    private ExecutionArtifactAppendVO persist(ExecutionArtifactAppendDTO request, TaskEntity task) {
        ExecutionArtifactEntity existing = findBySequence(request.executionId(), request.sequenceNo());
        if (existing != null) {
            return requireIdempotent(existing, request, task.getId());
        }
        long count = artifactMapper.selectCount(new LambdaQueryWrapper<ExecutionArtifactEntity>()
                .eq(ExecutionArtifactEntity::getExecutionId, request.executionId()));
        if (count >= TaskConstant.MAX_EXECUTION_ARTIFACT_COUNT) {
            throw new BusinessException(ErrorCode.CONFLICT, "单次执行产物数量超过限制");
        }

        ExecutionArtifactEntity entity = new ExecutionArtifactEntity();
        entity.setSpaceId(task.getSpaceId());
        entity.setTaskId(task.getId());
        entity.setExecutionId(request.executionId());
        entity.setSourceTaskId(request.sourceTaskId());
        entity.setSequenceNo(request.sequenceNo());
        entity.setSourceToolCallId(request.sourceToolCallId());
        entity.setArtifactType(request.artifactType());
        entity.setSchemaVersion(request.schemaVersion());
        entity.setPayloadJson(request.payloadJson());
        entity.setPayloadSha256(request.payloadSha256());
        try {
            artifactMapper.insert(entity);
            return toAppendVO(entity);
        } catch (DuplicateKeyException exception) {
            ExecutionArtifactEntity concurrent = findBySequence(request.executionId(), request.sequenceNo());
            if (concurrent == null) {
                throw exception;
            }
            return requireIdempotent(concurrent, request, task.getId());
        }
    }

    /**
     * 按任务ID，按sequenceNo升序查询该任务全部隔离执行产物
     * @param taskId 任务ID
     * @return 产物VO列表，按序号稳定排序
     */
    public List<ExecutionArtifactVO> list(Long taskId) {
        TaskEntity task = taskService.requireReadable(taskId);
        return artifactMapper.selectList(new LambdaQueryWrapper<ExecutionArtifactEntity>()
                        .eq(ExecutionArtifactEntity::getTaskId, task.getId())
                        .orderByAsc(ExecutionArtifactEntity::getSequenceNo))
                .stream().map(ExecutionArtifactVO::from).toList();
    }

    /**
     * 校验产物请求参数、类型、序号、payload结构与哈希，校验执行归属
     * @param task 任务实体
     * @param request 产物追加DTO
     * @throws BusinessException 参数缺失、类型非法、序号越界、payload超限、hash不匹配、执行归属不匹配
     */
    private void validateRequest(TaskEntity task, ExecutionArtifactAppendDTO request) {
        if (request == null || request.executionId() == null || request.sourceTaskId() == null
                || request.sequenceNo() == null || request.sequenceNo() < 1
                || request.sequenceNo() > TaskConstant.MAX_EXECUTION_ARTIFACT_COUNT
                || request.schemaVersion() == null || request.schemaVersion() < 1
                || request.artifactType() == null || request.payloadJson() == null
                || request.payloadSha256() == null || request.payloadSha256().length() != 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "隔离执行产物契约不完整");
        }
        try {
            ExecutionArtifactType.valueOf(request.artifactType());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "隔离执行产物类型不受支持");
        }
        boolean resultSummary = ExecutionArtifactType.RESULT_SUMMARY.name().equals(request.artifactType());
        if (resultSummary && request.sequenceNo() != TaskConstant.RESULT_SUMMARY_ARTIFACT_SEQUENCE
                || !resultSummary && request.sequenceNo() > TaskConstant.MAX_TOOL_EXECUTION_ARTIFACT_SEQUENCE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "隔离执行产物序号不合法");
        }
        if (!task.getParentTaskId().equals(request.sourceTaskId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "隔离执行产物来源任务不匹配");
        }
        if (request.payloadJson().getBytes(StandardCharsets.UTF_8).length
                > TaskConstant.MAX_EXECUTION_ARTIFACT_PAYLOAD_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "隔离执行产物内容超过限制");
        }
        JsonNode payload = JsonUtils.parse(request.payloadJson(), JsonNode.class);
        if (payload == null || !payload.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "隔离执行产物必须是 JSON 对象");
        }
        String expectedHash = StableSnapshotUtils.snapshotHash(request.schemaVersion(), payload);
        if (!expectedHash.equals(request.payloadSha256())) {
            throw new BusinessException(ErrorCode.CONFLICT, "隔离执行产物 hash 不匹配");
        }
        AgentExecutionReplayIdentityVO identity = requireData(agentFeign.getReplayIdentity(task.getId()));
        if (identity.executionCount() != 1 || !request.executionId().equals(identity.executionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "隔离执行产物不属于当前任务执行");
        }
    }

    /**
     * 校验任务类型：仅允许ISOLATED隔离+REPLAY回放血缘并且存在父任务的任务追加产物
     * @param task 待校验任务实体
     * @throws BusinessException 不满足回放隔离约束则抛出
     */
    private void requireIsolatedReplay(TaskEntity task) {
        if (!TaskExecutionMode.ISOLATED.name().equals(task.getExecutionMode())
                || !TaskLineageType.REPLAY.name().equals(task.getLineageType())
                || task.getParentTaskId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅 Replay 隔离执行可以追加产物");
        }
    }

    /**
     * 根据executionId+sequenceNo查询唯一产物记录
     * @param executionId Agent执行ID
     * @param sequenceNo 产物序号
     * @return 产物实体，不存在返回null
     */
    private ExecutionArtifactEntity findBySequence(Long executionId, Integer sequenceNo) {
        return artifactMapper.selectOne(new LambdaQueryWrapper<ExecutionArtifactEntity>()
                .eq(ExecutionArtifactEntity::getExecutionId, executionId)
                .eq(ExecutionArtifactEntity::getSequenceNo, sequenceNo));
    }

    /**
     * 幂等校验：已有记录时，只有所有核心字段完全一致才允许返回；字段不一致判定为冲突
     * @param existing 数据库已存在产物实体
     * @param request 本次追加请求DTO
     * @param taskId 当前任务ID
     * @return 幂等成功返回产物VO
     * @throws BusinessException 序号已占用但内容不一致，抛出冲突异常并打印告警日志
     */
    private ExecutionArtifactAppendVO requireIdempotent(ExecutionArtifactEntity existing,
                                                        ExecutionArtifactAppendDTO request, Long taskId) {
        if (!existing.getTaskId().equals(taskId)
                || !existing.getSourceTaskId().equals(request.sourceTaskId())
                || !existing.getArtifactType().equals(request.artifactType())
                || !existing.getSchemaVersion().equals(request.schemaVersion())
                || !existing.getPayloadSha256().equals(request.payloadSha256())) {
            log.warn("隔离执行产物幂等冲突 taskId={}, executionId={}, sequenceNo={}",
                    existing.getTaskId(), request.executionId(), request.sequenceNo());
            throw new BusinessException(ErrorCode.CONFLICT, "隔离执行产物序号已被不同内容占用");
        }
        return toAppendVO(existing);
    }

    /**
     * 数据库实体转换为追加结果VO
     * @param entity 产物实体
     * @return ExecutionArtifactAppendVO
     */
    private ExecutionArtifactAppendVO toAppendVO(ExecutionArtifactEntity entity) {
        return new ExecutionArtifactAppendVO(entity.getId(), entity.getArtifactType(), entity.getPayloadSha256());
    }

    /**
     * 校验Feign远程调用返回结果，成功且data非空才返回数据
     * @param result 远程返回Result包装
     * @return 响应data
     * @param <T> data类型
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "无法确认 AgentExecution 归属");
        }
        return result.data();
    }
}
