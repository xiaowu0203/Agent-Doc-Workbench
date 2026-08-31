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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 平台角色管理服务。
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
     * 查询全部有效平台角色。
     *
     * @return 平台角色列表
     */
    public List<PlatformRoleEntity> list() {
        return platformRoleMapper.selectList(new LambdaQueryWrapper<PlatformRoleEntity>()
                .orderByDesc(PlatformRoleEntity::getProtectedRole)
                .orderByAsc(PlatformRoleEntity::getCreatedAt));
    }

    /**
     * 查询平台角色详情。
     *
     * @param roleId 平台角色 ID
     * @return 平台角色实体
     */
    public PlatformRoleEntity detail(Long roleId) {
        PlatformRoleEntity role = platformRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "平台角色不存在");
        }
        return role;
    }

    /**
     * 创建自定义平台角色。新建角色不允许直接成为受保护角色。
     *
     * @param roleKey     平台角色稳定标识
     * @param displayName 平台角色展示名称
     * @return 新建的平台角色
     */
    @Transactional(rollbackFor = Exception.class)
    public PlatformRoleEntity create(String roleKey, String displayName) {
        Long duplicate = platformRoleMapper.selectCount(new LambdaQueryWrapper<PlatformRoleEntity>()
                .eq(PlatformRoleEntity::getRoleKey, roleKey));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "平台角色标识已存在");
        }
        PlatformRoleEntity role = new PlatformRoleEntity();
        role.setRoleKey(roleKey);
        role.setDisplayName(displayName);
        role.setProtectedRole(false);
        platformRoleMapper.insert(role);
        return role;
    }

    /**
     * 修改平台角色展示名称。受保护角色禁止修改。
     *
     * @param roleId     平台角色 ID
     * @param displayName 新展示名称
     * @return 更新后的平台角色
     */
    @Transactional(rollbackFor = Exception.class)
    public PlatformRoleEntity update(Long roleId, String displayName) {
        PlatformRoleEntity role = requireMutableRole(roleId);
        role.setDisplayName(displayName);
        platformRoleMapper.updateById(role);
        return role;
    }

    /**
     * 删除平台角色。受保护角色或仍有用户绑定的角色禁止删除。
     *
     * @param roleId 平台角色 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long roleId) {
        PlatformRoleEntity role = requireMutableRole(roleId);
        Long bindingCount = userPlatformRoleMapper.selectCount(new LambdaQueryWrapper<UserPlatformRoleEntity>()
                .eq(UserPlatformRoleEntity::getRoleId, roleId));
        if (bindingCount != null && bindingCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "平台角色仍有用户绑定，不能删除");
        }
        platformRoleMapper.deleteById(role);
    }

    /**
     * 判断当前登录用户是否拥有指定平台角色，供 Controller 的权限表达式调用。
     *
     * @param roleKey 平台角色标识
     * @return true 表示当前用户拥有该角色
     */
    public boolean hasCurrentUserRole(String roleKey) {
        Long userId = AuthUtils.getUserId();
        return userId != null && roleKey != null && listRoleKeys(userId).contains(roleKey);
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

    private PlatformRoleEntity requireMutableRole(Long roleId) {
        PlatformRoleEntity role = detail(roleId);
        if (Boolean.TRUE.equals(role.getProtectedRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "受保护平台角色不可修改或删除");
        }
        return role;
    }

}
