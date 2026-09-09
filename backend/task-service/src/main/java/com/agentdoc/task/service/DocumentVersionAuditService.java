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

/**
 * 文档版本操作的统一审计入口。
 * <p>专门处理文档版本相关操作的审计日志记录，统一做参数校验、权限校验、日志落库。</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentVersionAuditService {

    private final DocumentFeign documentFeign;
    private final AuditLogService auditLogService;

    /**
     * 记录文档版本回滚操作审计日志
     * <p>校验入参完整性 → 校验空间编辑权限 → 写入审计日志，日志携带文档ID、目标版本号、回滚来源版本号</p>
     * @param request 文档版本回滚审计入参
     */
    public void recordRollback(DocumentVersionRollbackAuditDTO request) {
        // 参数完整性校验，必填字段不能为null
        if (request == null || request.spaceId() == null || request.documentId() == null
                || request.versionId() == null || request.versionNo() == null
                || request.rollbackFromVersion() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档回滚审计参数不完整");
        }
        // 校验当前用户拥有该空间的文档编辑权限
        requireData(documentFeign.checkSpacePermission(request.spaceId(), DOCUMENT_EDIT));
        // 记录人工操作审计日志，附加回滚上下文信息JSON
        auditLogService.recordHuman(request.spaceId(), AuditAction.DOCUMENT_VERSION_ROLLED_BACK,
                AuditTargetType.DOCUMENT_VERSION, request.versionId(), JsonUtils.toJson(Map.of(
                        "documentId", request.documentId(),
                        "versionNo", request.versionNo(),
                        "rollbackFromVersion", request.rollbackFromVersion())));
    }

    /**
     * Feign远程调用结果断言工具
     * 校验返回结果状态，非成功则抛出业务异常；成功返回响应数据
     * @param result 远程服务返回结果
     * @return 响应的业务数据
     * @param <T> 泛型返回数据类型
     */
    private <T> T requireData(Result<T> result) {
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "权限校验服务调用失败" : result.message());
        }
        return result.data();
    }
}
