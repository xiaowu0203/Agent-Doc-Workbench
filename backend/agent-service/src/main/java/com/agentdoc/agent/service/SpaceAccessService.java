package com.agentdoc.agent.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * 空间访问权限校验服务
 * <p>
 * 负责校验当前操作用户针对文档空间的权限；通过feign远程调用文档服务的权限校验接口完成鉴权。
 * 按稳定权限标识符校验；权限不满足直接抛出业务异常阻断后续业务流程。
 * </p>
 */
@Component("SpaceAccess")
@Service
@RequiredArgsConstructor
public class SpaceAccessService {

    private final DocumentFeign documentFeign;

    /**
     * 供方法级权限表达式使用的空间权限判断。
     *
     * @param spaceId 目标空间 ID
     * @param permissionCode 权限标识符
     * @return true 表示当前用户拥有目标空间权限
     */
    public boolean hasPermission(Long spaceId, String permissionCode) {
        if (spaceId == null || permissionCode == null) {
            return false;
        }
        try {
            Result<Void> result = documentFeign.checkSpacePermission(spaceId, permissionCode);
            return result != null && result.code() == ErrorCode.SUCCESS.getCode();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 通用空间权限校验。
     * <p>远程调用文档服务权限校验接口，传入空间ID与权限标识符；
     * feign返回结果为空或者返回码非成功，组装抛出业务异常，透传远端错误码和消息。</p>
     * @param spaceId 目标空间ID
     * @param permissionCode 权限标识符
     * @throws BusinessException 权限不满足、远程调用异常抛出
     */
    public void requirePermission(Long spaceId, String permissionCode) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, permissionCode);
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "空间权限校验失败" : result.message());
        }
    }
}
