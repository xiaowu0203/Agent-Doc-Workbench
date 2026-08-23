package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.document.enums.SpaceRole;
import com.agentdoc.document.mapper.MemberMapper;
import com.agentdoc.document.pojo.entity.MemberEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 空间成员权限校验服务：所有空间内操作统一经本服务校验当前登录用户的成员角色。
 * <p>
 * 角色绑定到具体空间（member 表），非用户全局属性；
 * 空间操作权限分级：{@link SpaceRole#OWNER} &gt; {@link SpaceRole#EDITOR} &gt; {@link SpaceRole#VIEWER}。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SpacePermissionService {

    private final MemberMapper memberMapper;

    /**
     * 查询用户在指定空间的角色，非成员返回 null。
     * @param spaceId 空间 ID
     * @param userId 用户 ID
     * @return 成员角色；非成员返回 null
     */
    public SpaceRole getRole(Long spaceId, Long userId) {
        MemberEntity member = memberMapper.selectOne(new LambdaQueryWrapper<MemberEntity>()
                .eq(MemberEntity::getSpaceId, spaceId)
                .eq(MemberEntity::getUserId, userId));
        return member == null ? null : SpaceRole.fromCode(member.getRole());
    }

    /**
     * 校验当前登录用户是空间成员，返回其角色。
     * @param spaceId 空间 ID
     * @return 当前用户的成员角色
     * @throws BusinessException 未登录（{@link ErrorCode#UNAUTHORIZED}）或非空间成员（{@link ErrorCode#FORBIDDEN}）
     */
    public SpaceRole requireMember(Long spaceId) {
        Long userId = requireUserId();
        SpaceRole role = getRole(spaceId, userId);
        if (role == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不是该空间成员");
        }
        return role;
    }

    /**
     * 校验当前登录用户的角色不低于 required（OWNER 满足一切要求，VIEWER 仅满足只读要求）。
     * @param spaceId 空间 ID
     * @param required 最低要求角色
     * @return 当前用户的成员角色
     * @throws BusinessException 未登录 / 非成员 / 角色权限不足（{@link ErrorCode#FORBIDDEN}）
     */
    public SpaceRole requireRole(Long spaceId, SpaceRole required) {
        SpaceRole role = requireMember(spaceId);
        if (role.getCode() > required.getCode()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要角色：" + required.getName() + " 及以上");
        }
        return role;
    }

    /**
     * 取当前登录用户 ID（委托 {@link AuthUtils#getUserIdOrException()}，未登录抛 401）。
     * @return 用户 ID
     * @throws BusinessException 未登录，{@link ErrorCode#UNAUTHORIZED}
     */
    public Long requireUserId() {
        return AuthUtils.getUserIdOrException();
    }
}
