package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.utils.StableSnapshotUtils;
import com.agentdoc.document.enums.DocumentVersionActorType;
import com.agentdoc.document.constant.DocumentConstant;
import com.agentdoc.document.enums.DocumentVersionSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;
import static com.agentdoc.common.utils.OnlineOriginalChangeUtils.*;

/** 只读取 Agent 收尾时保存的版本，不读取当前正文或用户临时草稿。 */
@Service
@RequiredArgsConstructor
public class DocumentOnlineOriginalDraftService {
    private final TaskFeign tasks;
    private final DocumentService documents;
    private final DocumentVersionService versions;
    private final SpacePermissionService permissions;

    public OnlineOriginalChangesVO read(Long taskId, Long spaceId, Long executionId) {
        AuthUtils.getUserIdOrException();
        var response = tasks.getOnlineOriginalInput(taskId, spaceId, executionId);
        if (response == null || response.code() != ErrorCode.SUCCESS.getCode() || response.data() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "原始草稿任务访问被拒绝");
        }
        var input = response.data();
        if (!taskId.toString().equals(input.taskId()) || !spaceId.toString().equals(input.spaceId())
                || !executionId.toString().equals(input.executionId())) { throw new BusinessException(ErrorCode.FORBIDDEN, INVALID); }
        var doc = documents.requireDoc(Long.valueOf(input.documentId()));
        if (!Objects.equals(doc.getSpaceId(), spaceId)) { throw new BusinessException(ErrorCode.FORBIDDEN, "原文档已移出空间"); }
        permissions.requirePermission(spaceId, DOCUMENT_READ);
        if (!input.completed() || !"DRAFT".equals(input.documentType())) { return new OnlineOriginalChangesVO(SCHEMA_VERSION, UNAVAILABLE, input, List.of()); }
        var rows = versions.agentDraftSnapshots(doc.getId(), taskId);
        if (rows.isEmpty()) { return new OnlineOriginalChangesVO(SCHEMA_VERSION, UNAVAILABLE, input, List.of()); }
        var row = rows.getFirst();
        if (rows.size() != 1 || !DocumentVersionSourceType.AGENT_DRAFT.name().equals(row.getSourceType())
                || !DocumentVersionActorType.AGENT.name().equals(row.getActorType()) || !Objects.equals(row.getSourceTaskId(), taskId)
                || !Objects.equals(row.getActorId(), Long.valueOf(input.agentId())) || !Objects.equals(row.getDocumentId(), doc.getId())
                || input.baseVersion() == null || !Objects.equals(row.getVersionNo(), Math.addExact(input.baseVersion(), DocumentConstant.VERSION_INCREMENT))
                || row.getContent() == null || !Objects.equals(StableSnapshotUtils.sha256Utf8(row.getContent()), row.getContentSha256())
                || row.getCreatedAt() == null) { return new OnlineOriginalChangesVO(SCHEMA_VERSION, INVALID, input, List.of()); }
        var item = signed(input, row.getId().toString(), DRAFT, input.baseVersion(), row.getVersionNo(), row.getContentSha256(),
                row.getContentSha256(), true, row.getCreatedAt().toString());
        return new OnlineOriginalChangesVO(SCHEMA_VERSION, AVAILABLE, input, List.of(item));
    }
}
