package com.agentdoc.agent.service;

import com.agentdoc.agent.mapper.AgentExecutionMapper;
import com.agentdoc.agent.mapper.AgentOnlineOriginalTextMapper;
import com.agentdoc.agent.pojo.entity.AgentExecutionEntity;
import com.agentdoc.agent.pojo.entity.AgentOnlineOriginalTextEntity;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.AgentOnlineOriginalTextVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.OnlineOriginalTextUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static com.agentdoc.common.utils.OnlineOriginalTextUtils.*;
import static com.agentdoc.common.enums.OnlineReasonCode.ACCESS_DENIED;
import static com.agentdoc.common.enums.OnlineReasonCode.ORIGINAL_EVIDENCE_INVALID;

/** 最终输出捕获与当前人类资源授权；不从历史 resultSummary 回填。 */
@Service
@RequiredArgsConstructor
public class AgentOnlineOriginalTextService {
    private final AgentOnlineOriginalTextMapper originals;
    private final AgentExecutionMapper executions;
    private final TaskFeign tasks;

    /** 独立提交：捕获失败由调用者记录，不改变原执行终态。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void capture(AgentExecutionEntity execution, String text) {
        if (execution.getOnlineAssignmentId() == null || text == null) { return; }
        if (!"COMPLETED".equals(execution.getStatus()) || execution.getFinishedAt() == null
                || !Integer.valueOf(SCHEMA_VERSION).equals(execution.getOnlineBindingSchemaVersion())) { throw invalid(); }
        var row = new AgentOnlineOriginalTextEntity();
        row.setId(execution.getId()); row.setTaskId(execution.getWorkbenchTaskId()); row.setSpaceId(execution.getSpaceId());
        row.setAgentId(execution.getAgentId()); row.setExperimentId(execution.getOnlineExperimentId());
        row.setAssignmentId(execution.getOnlineAssignmentId()); row.setBindingHash(execution.getOnlineBindingHash());
        row.setOriginalText(text); row.setContentHash(StableSnapshotUtils.sha256Utf8(text));
        row.setCapturedAt(execution.getFinishedAt().truncatedTo(ChronoUnit.MILLIS));
        row.setIdentityHash(OnlineOriginalTextUtils.identityHash(view(row)));
        try { originals.insert(row); }
        catch (DuplicateKeyException race) {
            var prior = originals.selectById(row.getId());
            if (prior == null || !Objects.equals(prior.getTaskId(), row.getTaskId())
                    || !Objects.equals(prior.getSpaceId(), row.getSpaceId()) || !Objects.equals(prior.getAgentId(), row.getAgentId())
                    || !Objects.equals(prior.getExperimentId(), row.getExperimentId()) || !Objects.equals(prior.getAssignmentId(), row.getAssignmentId())
                    || !Objects.equals(prior.getBindingHash(), row.getBindingHash()) || !Objects.equals(prior.getOriginalText(), text)
                    || !OnlineOriginalTextUtils.valid(view(prior))) { throw invalid(); }
        }
    }

    public AgentOnlineOriginalTextVO read(Long taskId, Long spaceId) {
        AuthUtils.getUserIdOrException();
        var execution = executions.selectOne(new LambdaQueryWrapper<AgentExecutionEntity>()
                .eq(AgentExecutionEntity::getWorkbenchTaskId, taskId));
        if (execution == null || !Objects.equals(spaceId, execution.getSpaceId()) || execution.getOnlineAssignmentId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, UNAVAILABLE);
        }
        Result<Void> permission = tasks.checkOriginalEvidencePermission(taskId, spaceId, execution.getId());
        if (permission == null || permission.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(permission == null ? ErrorCode.INTERNAL_ERROR.getCode() : permission.code(),
                    ACCESS_DENIED.name());
        }
        var row = originals.selectById(execution.getId());
        if (row == null) { return new AgentOnlineOriginalTextVO(SCHEMA_VERSION, UNAVAILABLE, null, taskId.toString(),
                execution.getId().toString(), spaceId.toString(), execution.getAgentId().toString(),
                execution.getOnlineExperimentId().toString(), execution.getOnlineAssignmentId().toString(),
                execution.getOnlineBindingHash(), null, null, null, null); }
        if (!Objects.equals(row.getTaskId(), taskId) || !Objects.equals(row.getSpaceId(), spaceId)
                || !Objects.equals(row.getAgentId(), execution.getAgentId()) || !Objects.equals(row.getExperimentId(), execution.getOnlineExperimentId())
                || !Objects.equals(row.getAssignmentId(), execution.getOnlineAssignmentId())
                || !Objects.equals(row.getBindingHash(), execution.getOnlineBindingHash()) || !OnlineOriginalTextUtils.valid(view(row))) { throw invalid(); }
        return view(row);
    }

    private static AgentOnlineOriginalTextVO view(AgentOnlineOriginalTextEntity row) {
        return new AgentOnlineOriginalTextVO(SCHEMA_VERSION, AVAILABLE, row.getId().toString(), row.getTaskId().toString(),
                row.getId().toString(), row.getSpaceId().toString(), row.getAgentId().toString(), row.getExperimentId().toString(),
                row.getAssignmentId().toString(), row.getBindingHash(), row.getContentHash(), row.getIdentityHash(),
                row.getCapturedAt().toString(), row.getOriginalText());
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.CONFLICT, ORIGINAL_EVIDENCE_INVALID.name()); }
}
