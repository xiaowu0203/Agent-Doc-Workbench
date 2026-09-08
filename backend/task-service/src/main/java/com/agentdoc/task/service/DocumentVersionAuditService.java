package com.agentdoc.task.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import com.agentdoc.common.feign.dto.DocumentVersionRollbackAuditDTO;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.task.enums.AuditAction;
import com.agentdoc.task.enums.AuditTargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_EDIT;

/** 文档版本操作的统一审计入口。 */
@Service
@RequiredArgsConstructor
public class DocumentVersionAuditService {

    private final DocumentFeign documentFeign;
    private final AuditLogService auditLogService;

    public void recordRollback(DocumentVersionRollbackAuditDTO request) {
        if (request == null || request.spaceId() == null || request.documentId() == null
                || request.versionId() == null || request.versionNo() == null
                || request.rollbackFromVersion() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档回滚审计参数不完整");
        }
        requireData(documentFeign.checkSpacePermission(request.spaceId(), DOCUMENT_EDIT));
        auditLogService.recordHuman(request.spaceId(), AuditAction.DOCUMENT_VERSION_ROLLED_BACK,
                AuditTargetType.DOCUMENT_VERSION, request.versionId(), JsonUtils.toJson(Map.of(
                        "documentId", request.documentId(),
                        "versionNo", request.versionNo(),
                        "rollbackFromVersion", request.rollbackFromVersion())));
    }

    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "权限校验服务调用失败" : result.message());
        }
        return result.data();
    }
}
