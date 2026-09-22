package com.agentdoc.evaluation.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 空间权限校验服务，委托documentFeign跨服务校验空间权限。
 * 对外暴露requirePermission方法，无权限/调用异常直接抛出业务异常阻断流程。
 */
@Component("EvaluationSpaceAccess")
@RequiredArgsConstructor
public class SpaceAccessService {

    private final DocumentFeign documentFeign;

    /**
     * 校验用户在指定空间下是否拥有目标权限
     * @param spaceId 空间ID
     * @param permission 权限标识，如 EVALUATION_RUN / EVALUATION_READ
     * @throws BusinessException 校验不通过或远程调用异常时抛出
     */
    public void requirePermission(Long spaceId, String permission) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, permission);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(
                    result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "空间权限校验失败" : result.message()
            );
        }
    }
}
