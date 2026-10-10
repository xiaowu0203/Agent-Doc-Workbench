package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.task.mapper.TaskMapper;
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

    public void require(Long taskId, Long spaceId, Long executionId) {
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
    }

    private static void permission(Result<?> value) {
        if (value == null || value.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(value == null ? ErrorCode.INTERNAL_ERROR.getCode() : value.code(), "原始证据访问被拒绝");
        }
    }
}
