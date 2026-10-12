package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.feign.vo.OnlineApprovalDifferenceVO;
import com.agentdoc.common.utils.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.EVALUATION_RUN;

/** 只从 Task 已授权的实际来源计算差异，调用者无法伪造原始正文。 */
@Service
@RequiredArgsConstructor
public class DocumentOnlineApprovalDifferenceService {
    private final TaskFeign tasks;
    private final DocumentService documents;
    private final SpacePermissionService permissions;

    public OnlineApprovalDifferenceVO get(Long id) {
        AuthUtils.getUserIdOrException();
        var result = tasks.getOnlineApprovalSource(id);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode() || result.data() == null) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(), "原始审批来源不可访问");
        }
        var source = result.data();
        if (source.input() == null || !id.toString().equals(source.changeRequestId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "原始审批来源身份不一致");
        }
        Long spaceId = Long.valueOf(source.input().spaceId());
        permissions.requirePermission(spaceId, EVALUATION_RUN);
        permissions.requirePermission(spaceId, CHANGE_REQUEST_READ);
        return documents.compareOnlineApproval(source);
    }
}
