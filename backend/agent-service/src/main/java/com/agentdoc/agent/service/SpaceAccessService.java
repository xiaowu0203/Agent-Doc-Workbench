package com.agentdoc.agent.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.SpaceRole;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.DocumentFeign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 空间访问权限校验服务
 * <p>
 * 负责校验当前操作用户针对文档空间的权限；通过feign远程调用文档服务的权限校验接口完成鉴权。
 * 提供语义化方法，校验空间所有者权限、空间查看者权限；权限不满足直接抛出业务异常阻断后续业务流程。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SpaceAccessService {

    private final DocumentFeign documentFeign;

    /**
     * 校验当前用户必须是该空间的所有者(OWNER)权限，权限不足抛出业务异常
     * @param spaceId 目标空间ID
     * @throws BusinessException 非所有者、接口调用异常时抛出
     */
    public void requireOwner(Long spaceId) {
        requireRole(spaceId, SpaceRole.OWNER);
    }

    /**
     * 校验当前用户至少具备空间查看者(VIEWER)权限，查看者及以上权限可通过；权限不足抛出业务异常
     * @param spaceId 目标空间ID
     * @throws BusinessException 无查看权限、接口调用异常时抛出
     */
    public void requireViewer(Long spaceId) {
        requireRole(spaceId, SpaceRole.VIEWER);
    }

    /**
     * 通用空间权限校验私有实现
     * <p>远程调用文档服务权限校验接口，传入空间ID与角色编码；
     * feign返回结果为空或者返回码非成功，组装抛出业务异常，透传远端错误码和消息。</p>
     * @param spaceId 目标空间ID
     * @param role 需要校验的最低角色权限
     * @throws BusinessException 权限不满足、远程调用异常抛出
     */
    private void requireRole(Long spaceId, SpaceRole role) {
        Result<Void> result = documentFeign.checkSpacePermission(spaceId, role.getCode());
        if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
            throw new BusinessException(result == null ? ErrorCode.INTERNAL_ERROR.getCode() : result.code(),
                    result == null ? "空间权限校验失败" : result.message());
        }
    }
}
