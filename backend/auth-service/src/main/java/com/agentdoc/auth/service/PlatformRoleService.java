package com.agentdoc.auth.service;

import com.agentdoc.auth.mapper.PlatformRoleMapper;
import com.agentdoc.auth.mapper.UserPlatformRoleMapper;
import com.agentdoc.auth.pojo.entity.PlatformRoleEntity;
import com.agentdoc.auth.pojo.entity.UserPlatformRoleEntity;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AuthUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 平台角色查询服务。
 */
@Service
@RequiredArgsConstructor
public class PlatformRoleService {

    private final PlatformRoleMapper platformRoleMapper;
    private final UserPlatformRoleMapper userPlatformRoleMapper;

    /**
     * 查询用户当前有效的平台角色标识。
     *
     * @param userId 用户 ID
     * @return 平台角色标识列表
     */
    public List<String> listRoleKeys(Long userId) {
        List<Long> roleIds = userPlatformRoleMapper.selectList(
                        new LambdaQueryWrapper<UserPlatformRoleEntity>()
                                .eq(UserPlatformRoleEntity::getUserId, userId))
                .stream()
                .map(UserPlatformRoleEntity::getRoleId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return platformRoleMapper.selectBatchIds(roleIds).stream()
                .map(PlatformRoleEntity::getRoleKey)
                .sorted()
                .toList();
    }

    /**
     * 校验当前用户是否拥有roleKey角色。
     * @param roleKey 平台角色标识
     */
    public void requireCurrentUserRole(String roleKey) {
        Long userId = AuthUtils.getUserIdOrException();
        if (!listRoleKeys(userId).contains(roleKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少平台角色：" + roleKey);
        }
    }

}
