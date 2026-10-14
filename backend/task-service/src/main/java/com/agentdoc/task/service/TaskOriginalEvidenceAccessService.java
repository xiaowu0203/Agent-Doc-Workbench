package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.vo.OnlineOriginalInputVO;
import com.agentdoc.common.feign.vo.OnlineReviewGoalVO;
import com.agentdoc.common.enums.DocType;
import com.agentdoc.common.enums.TaskExecutionMode;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.mapper.TaskMapper;
import com.agentdoc.task.pojo.entity.TaskEntity;
import com.agentdoc.task.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;

/** 原始证据的当前 Task/Execution 和文档授权；不使用历史能力扩大读权限。 */
@Service
@RequiredArgsConstructor
public class TaskOriginalEvidenceAccessService {
    private final TaskMapper tasks;
    private final DocumentFeign documents;

    public TaskEntity require(Long taskId, Long spaceId, Long executionId) {
        AuthUtils.getUserIdOrException();
        var task = tasks.selectById(taskId);
        if (task == null || !Objects.equals(task.getSpaceId(), spaceId) || task.getOnlineAssignmentId() == null
                || executionId == null || !Objects.equals(task.getAgentExecutionId(), executionId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "原始证据任务不存在");
        }
        permission(documents.checkSpacePermission(spaceId, TASK_READ));
        permission(documents.checkSpacePermission(spaceId, DOCUMENT_READ));
        var refs = documents.getDocumentRefs(List.of(task.getDocumentId()));
        if (refs == null || refs.code() != ErrorCode.SUCCESS.getCode() || refs.data() == null || refs.data().size() != 1
                || !Objects.equals(refs.data().getFirst().id(), task.getDocumentId())
                || !Objects.equals(refs.data().getFirst().spaceId(), spaceId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "原文档已删除或移出空间");
        }
        var frozen = documents.getVersionExecutionContext(task.getDocumentId(), task.getDocumentVersionSnapshot(), task.getDocumentContentSha256());
        permission(frozen);
        if (frozen.data() == null || !Objects.equals(frozen.data().documentId(), task.getDocumentId())
                || !Objects.equals(frozen.data().version(), task.getDocumentVersionSnapshot())
                || !Objects.equals(frozen.data().contentSha256(), task.getDocumentContentSha256())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "原始输入版本不可访问");
        }
        return task;
    }

    public OnlineOriginalInputVO input(Long taskId, Long spaceId, Long executionId) {
        var task = require(taskId, spaceId, executionId);
        if (!Integer.valueOf(2).equals(task.getOnlineBindingSchemaVersion()) || task.getOnlineExperimentId() == null
                || !TaskExecutionMode.LIVE.name().equals(task.getExecutionMode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "原始变更必须属于线上 LIVE 执行");
        }
        return new OnlineOriginalInputVO(task.getId().toString(), executionId.toString(), spaceId.toString(), task.getAgentId().toString(),
                task.getOnlineExperimentId().toString(), task.getOnlineAssignmentId().toString(), task.getOnlineBindingHash(),
                task.getDocumentId().toString(), DocType.fromCode(task.getDocumentType()).name(), task.getDocumentVersionSnapshot(),
                task.getDocumentContentSha256(), TaskStatus.COMPLETED.getCodeEquals(task.getStatus()));
    }

    public OnlineReviewGoalVO goal(Long taskId, Long spaceId, Long executionId) {
        var row = require(taskId, spaceId, executionId);
        if (!Integer.valueOf(1).equals(row.getInputSnapshotSchemaVersion()) || !Objects.equals(row.getInputSnapshotHash(), TaskService.calculateInputSnapshotHash(row))) {
            throw new BusinessException(ErrorCode.CONFLICT, "冻结用户目标身份无效");
        }
        return new OnlineReviewGoalVO(taskId.toString(), row.getInputSnapshotHash(), row.getInstruction());
    }
    private static void permission(Result<?> value) {
        if (value == null || value.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(value == null ? ErrorCode.INTERNAL_ERROR.getCode() : value.code(), "原始证据访问被拒绝");
        }
    }
}
