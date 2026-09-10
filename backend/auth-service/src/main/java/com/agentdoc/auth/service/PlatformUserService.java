package com.agentdoc.auth.service;

import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.mapper.DepartmentMapper;
import com.agentdoc.auth.mapper.PlatformRoleMapper;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.mapper.UserPlatformRoleMapper;
import com.agentdoc.auth.pojo.dto.PlatformUserCreateDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserPasswordResetDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserRoleReplaceDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserStatusUpdateDTO;
import com.agentdoc.auth.pojo.dto.PlatformUserUpdateDTO;
import com.agentdoc.auth.pojo.entity.DepartmentEntity;
import com.agentdoc.auth.pojo.entity.PlatformRoleEntity;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.pojo.entity.UserPlatformRoleEntity;
import com.agentdoc.auth.pojo.param.PlatformUserSearchParam;
import com.agentdoc.auth.pojo.vo.DepartmentSummaryVO;
import com.agentdoc.auth.pojo.vo.PlatformUserStatsVO;
import com.agentdoc.auth.pojo.vo.PlatformUserVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.common.utils.PageUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.auth.constant.PlatformManagementConstant.USER_CREATED;
import static com.agentdoc.auth.constant.PlatformManagementConstant.USER_PASSWORD_RESET;
import static com.agentdoc.auth.constant.PlatformManagementConstant.USER_ROLES_REPLACED;
import static com.agentdoc.auth.constant.PlatformManagementConstant.USER_STATUS_CHANGED;
import static com.agentdoc.auth.constant.PlatformManagementConstant.USER_TARGET;
import static com.agentdoc.auth.constant.PlatformManagementConstant.USER_UPDATED;
import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

/**
 * 平台用户管理服务
 * 提供用户分页查询、统计、详情、新增、更新、状态变更、密码重置、角色替换等能力
 * 包含超级管理员保护校验，防止系统无可用超级管理员
 */
@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final PlatformRoleMapper platformRoleMapper;
    private final UserPlatformRoleMapper userPlatformRoleMapper;
    private final DepartmentService departmentService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final PlatformAuditLogService auditLogService;

    /**
     * 平台用户分页搜索
     * 支持关键词、状态、部门、平台角色过滤；按创建时间、ID倒序
     * @param param 查询参数
     * @return 分页用户VO结果
     */
    public PageVO<PlatformUserVO> search(PlatformUserSearchParam param) {
        param.validate();
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .orderByDesc(UserEntity::getCreatedAt)
                .orderByDesc(UserEntity::getId);

        // 关键词模糊查询：用户名、昵称、邮箱
        if (param.getKeyword() != null && !param.getKeyword().isBlank()) {
            String keyword = param.getKeyword().trim();
            wrapper.and(query -> query.like(UserEntity::getUsername, keyword)
                    .or().like(UserEntity::getNickname, keyword)
                    .or().like(UserEntity::getEmail, keyword));
        }

        // 用户状态过滤
        if (param.getStatus() != null) {
            wrapper.eq(UserEntity::getStatus, UserStatus.fromCode(param.getStatus()).getCode());
        }

        // 部门过滤：0代表无部门
        if (param.getDepartmentId() != null) {
            if (param.getDepartmentId() == 0L) {
                wrapper.isNull(UserEntity::getDepartmentId);
            } else {
                wrapper.eq(UserEntity::getDepartmentId, param.getDepartmentId());
            }
        }

        // 根据角色key筛选用户ID
        List<Long> roleUserIds = findRoleUserIds(param.getPlatformRoleKey());
        if (roleUserIds != null) {
            if (roleUserIds.isEmpty()) {
                return PageVO.of(List.of(), 0, param);
            }
            wrapper.in(UserEntity::getId, roleUserIds);
        }
        Page<UserEntity> page = userMapper.selectPage(PageUtils.toPage(param), wrapper);
        return PageVO.of(toVOs(page.getRecords()), page.getTotal(), param);
    }

    /**
     * 获取平台用户统计数据
     * @return 用户统计VO：总用户、启用、禁用、未分配部门用户数量
     */
    public PlatformUserStatsVO stats() {
        // 总用户数
        long total = userMapper.selectCount(null);
        // 启用用户数
        long enabled = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getStatus, UserStatus.ENABLED.getCode()));
        // 禁用用户数
        long disabled = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getStatus, UserStatus.DISABLED.getCode()));
        // 未分配部门用户数
        long unassigned = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .isNull(UserEntity::getDepartmentId));
        return new PlatformUserStatsVO(total, enabled, disabled, unassigned);
    }

    /**
     * 查询单个用户详情
     * @param userId 用户ID
     * @return 用户详情VO
     */
    public PlatformUserVO detail(Long userId) {
        return toVOs(List.of(requireUser(userId))).getFirst();
    }

    /**
     * 创建平台用户
     * 校验用户名唯一、部门启用；支持设置超级管理员；创建后记录审计日志
     * @param dto 创建入参
     * @return 创建完成后的用户VO
     */
    @Transactional(rollbackFor = Exception.class)
    public PlatformUserVO create(PlatformUserCreateDTO dto) {
        requireUniqueUsername(dto.username());
        departmentService.requireEnabledDepartment(dto.departmentId());
        UserEntity user = new UserEntity();
        user.setUsername(dto.username().trim());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        // 昵称为空则使用用户名
        user.setNickname(dto.nickname() == null || dto.nickname().isBlank()
                ? user.getUsername() : dto.nickname().trim());
        user.setEmail(normalize(dto.email()));
        user.setDepartmentId(dto.departmentId());
        user.setJobTitle(normalize(dto.jobTitle()));
        // 状态为空默认启用
        user.setStatus(dto.status() == null
                ? UserStatus.ENABLED.getCode() : UserStatus.fromCode(dto.status()).getCode());

        // 禁用用户不能设置为超级管理员
        if (Boolean.TRUE.equals(dto.superAdmin()) && !UserStatus.isEnabled(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "禁用用户不能设为平台超级管理员");
        }
        userMapper.insert(user);

        // 如果标记为超级管理员，绑定超级管理员角色
        if (Boolean.TRUE.equals(dto.superAdmin())) {
            PlatformRoleEntity role = requireSuperAdminRole();
            UserPlatformRoleEntity binding = new UserPlatformRoleEntity();
            binding.setUserId(user.getId());
            binding.setRoleId(role.getId());
            userPlatformRoleMapper.insert(binding);
        }
        // 记录审计日志
        auditLogService.record(USER_CREATED, USER_TARGET, user.getId(),
                Map.of("username", user.getUsername(), "status", user.getStatus(),
                        "superAdmin", Boolean.TRUE.equals(dto.superAdmin())));
        return detail(user.getId());
    }

    /**
     * 更新用户基础信息（昵称、邮箱、部门、职位）
     * @param userId 用户ID
     * @param dto 更新入参
     * @return 更新后的用户VO
     */
    @Transactional(rollbackFor = Exception.class)
    public PlatformUserVO update(Long userId, PlatformUserUpdateDTO dto) {
        UserEntity user = requireUser(userId);
        departmentService.requireEnabledDepartment(dto.departmentId());
        user.setNickname(dto.nickname().trim());
        user.setEmail(normalize(dto.email()));
        user.setDepartmentId(dto.departmentId());
        user.setJobTitle(normalize(dto.jobTitle()));
        userMapper.updateById(user);
        auditLogService.record(USER_UPDATED, USER_TARGET, userId,
                Map.of("username", user.getUsername()));
        return detail(userId);
    }

    /**
     * 修改用户状态（启用/禁用）
     * 禁用超级管理员时，校验系统至少保留一名启用超级管理员；禁用后撤销该用户所有刷新令牌
     * @param userId 用户ID
     * @param dto 状态更新入参
     * @return 更新后的用户VO
     */
    @Transactional(rollbackFor = Exception.class)
    public PlatformUserVO updateStatus(Long userId, PlatformUserStatusUpdateDTO dto) {
        UserEntity user = requireUser(userId);
        int newStatus = UserStatus.fromCode(dto.status()).getCode();
        // 如果要把启用的超级管理员禁用，必须保证还有其他可用超级管理员
        if (newStatus == UserStatus.DISABLED.getCode()
                && UserStatus.isEnabled(user.getStatus()) && hasSuperAdminRole(userId)) {
            requireAnotherEnabledSuperAdmin(userId);
        }
        user.setStatus(newStatus);
        userMapper.updateById(user);
        // 用户禁用，作废全部refreshToken
        if (newStatus == UserStatus.DISABLED.getCode()) {
            refreshTokenService.revoke(userId);
        }
        auditLogService.record(USER_STATUS_CHANGED, USER_TARGET, userId,
                Map.of("status", newStatus));
        return detail(userId);
    }

    /**
     * 重置用户密码
     * 重置后撤销该用户所有刷新令牌，强制重新登录
     * @param userId 用户ID
     * @param dto 密码重置入参
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, PlatformUserPasswordResetDTO dto) {
        UserEntity user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        userMapper.updateById(user);
        // 修改密码，作废refreshToken
        refreshTokenService.revoke(userId);
        auditLogService.record(USER_PASSWORD_RESET, USER_TARGET, userId, Map.of());
    }

    /**
     * 替换用户平台角色
     * 当前业务仅支持超级管理员角色；
     * 取消超级管理员时，校验系统至少保留一名启用超级管理员；
     * 变更角色后撤销刷新令牌
     * @param userId 用户ID
     * @param dto 角色替换入参
     * @return 更新后的用户VO
     */
    @Transactional(rollbackFor = Exception.class)
    public PlatformUserVO replaceRoles(Long userId, PlatformUserRoleReplaceDTO dto) {
        UserEntity user = requireUser(userId);
        Set<String> roleKeys = dto.roleKeys().stream()
                .map(roleKey -> roleKey == null ? "" : roleKey.trim())
                .filter(roleKey -> !roleKey.isEmpty())
                .collect(Collectors.toSet());

        // 业务限制：只允许超级管理员角色
        if (roleKeys.stream().anyMatch(roleKey -> !SUPER_ADMIN.equals(roleKey))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前仅支持平台超级管理员角色");
        }
        boolean currentlySuperAdmin = hasSuperAdminRole(userId);
        boolean shouldBeSuperAdmin = roleKeys.contains(SUPER_ADMIN);

        // 禁用用户不能授予超级管理员
        if (shouldBeSuperAdmin && !UserStatus.isEnabled(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "禁用用户不能设为平台超级管理员");
        }

        // 取消超级管理员权限，需要校验保留至少一名启用超级管理员
        if (currentlySuperAdmin && !shouldBeSuperAdmin) {
            requireAnotherEnabledSuperAdmin(userId);
        }

        // 删除原有全部角色绑定
        userPlatformRoleMapper.delete(new LambdaQueryWrapper<UserPlatformRoleEntity>()
                .eq(UserPlatformRoleEntity::getUserId, userId));

        // 需要赋予超级管理员，则新增绑定
        if (shouldBeSuperAdmin) {
            PlatformRoleEntity role = requireSuperAdminRole();
            UserPlatformRoleEntity binding = new UserPlatformRoleEntity();
            binding.setUserId(userId);
            binding.setRoleId(role.getId());
            userPlatformRoleMapper.insert(binding);
        }

        // 角色变更，作废refreshToken
        refreshTokenService.revoke(userId);
        auditLogService.record(USER_ROLES_REPLACED, USER_TARGET, userId,
                Map.of("platformRoles", roleKeys.stream().sorted().toList()));
        return detail(userId);
    }

    /**
     * 校验用户存在并且状态为启用
     * @param userId 用户ID
     * @return 启用的用户实体
     */
    public UserEntity requireEnabledUser(Long userId) {
        UserEntity user = requireUser(userId);
        if (!UserStatus.isEnabled(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户已禁用");
        }
        return user;
    }

    /**
     * 校验用户必须存在，不存在抛出异常
     * @param userId 用户ID
     * @return 用户实体
     */
    public UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 校验用户名全局唯一
     * @param username 用户名
     */
    private void requireUniqueUsername(String username) {
        if (userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username.trim())) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
    }

    /**
     * 根据角色key查询拥有该角色的用户ID集合
     * @param roleKey 角色key
     * @return 用户ID列表；入参为空返回null；角色不存在返回空集合
     */
    private List<Long> findRoleUserIds(String roleKey) {
        if (roleKey == null || roleKey.isBlank()) {
            return null;
        }
        PlatformRoleEntity role = platformRoleMapper.selectOne(new LambdaQueryWrapper<PlatformRoleEntity>()
                .eq(PlatformRoleEntity::getRoleKey, roleKey.trim()));
        if (role == null) {
            return List.of();
        }
        return userPlatformRoleMapper.selectList(new LambdaQueryWrapper<UserPlatformRoleEntity>()
                        .select(UserPlatformRoleEntity::getUserId)
                        .eq(UserPlatformRoleEntity::getRoleId, role.getId()))
                .stream().map(UserPlatformRoleEntity::getUserId).distinct().toList();
    }

    /**
     * 判断用户是否拥有超级管理员角色
     * @param userId 用户ID
     * @return true：拥有超级管理员；false：没有
     */
    private boolean hasSuperAdminRole(Long userId) {
        PlatformRoleEntity role = requireSuperAdminRole();
        return userPlatformRoleMapper.selectCount(new LambdaQueryWrapper<UserPlatformRoleEntity>()
                .eq(UserPlatformRoleEntity::getUserId, userId)
                .eq(UserPlatformRoleEntity::getRoleId, role.getId())) > 0;
    }

    /**
     * 获取超级管理员角色实体，不存在抛出内部异常
     * @return 超级管理员角色
     */
    private PlatformRoleEntity requireSuperAdminRole() {
        PlatformRoleEntity role = platformRoleMapper.selectOne(new LambdaQueryWrapper<PlatformRoleEntity>()
                .eq(PlatformRoleEntity::getRoleKey, SUPER_ADMIN));
        if (role == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "平台超级管理员角色未初始化");
        }
        return role;
    }

    /**
     * 校验：排除指定用户后，系统至少存在一名启用的超级管理员
     * @param excludedUserId 需要排除的用户ID
     */
    private void requireAnotherEnabledSuperAdmin(Long excludedUserId) {
        List<Long> superAdminIds = findRoleUserIds(SUPER_ADMIN);
        if (superAdminIds == null || superAdminIds.stream().noneMatch(id -> !excludedUserId.equals(id))) {
            throw new BusinessException(ErrorCode.CONFLICT, "平台至少保留一名启用的超级管理员");
        }
        long enabledCount = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .in(UserEntity::getId, superAdminIds.stream()
                        .filter(id -> !excludedUserId.equals(id)).toList())
                .eq(UserEntity::getStatus, UserStatus.ENABLED.getCode()));
        if (enabledCount == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "平台至少保留一名启用的超级管理员");
        }
    }

    /**
     * 批量UserEntity转PlatformUserVO
     * 批量查询关联部门、角色信息，减少N+1查询
     * @param users 用户实体集合
     * @return 用户VO列表
     */
    private List<PlatformUserVO> toVOs(List<UserEntity> users) {
        if (users.isEmpty()) {
            return List.of();
        }

        // 批量加载部门信息
        List<Long> departmentIds = users.stream().map(UserEntity::getDepartmentId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, DepartmentEntity> departments = departmentIds.isEmpty() ? Map.of()
                : departmentMapper.selectBatchIds(departmentIds).stream()
                .collect(Collectors.toMap(DepartmentEntity::getId, Function.identity()));

        // 批量加载用户角色key
        Map<Long, List<String>> roles = loadRoleKeys(users.stream().map(UserEntity::getId).toList());
        return users.stream().map(user -> {
            DepartmentEntity department = user.getDepartmentId() == null
                    ? null : departments.get(user.getDepartmentId());
            return new PlatformUserVO(
                    user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getAvatarUrl(),
                    DepartmentSummaryVO.from(department), user.getJobTitle(), user.getStatus(),
                    roles.getOrDefault(user.getId(), List.of()), user.getLastLoginAt(),
                    user.getCreatedAt(), user.getUpdatedAt());
        }).toList();
    }

    /**
     * 批量加载用户对应的角色key集合
     * @param userIds 用户ID列表
     * @return key:userId value:角色key列表（去重排序）
     */
    private Map<Long, List<String>> loadRoleKeys(List<Long> userIds) {
        List<UserPlatformRoleEntity> bindings = userPlatformRoleMapper.selectList(
                new LambdaQueryWrapper<UserPlatformRoleEntity>()
                        .in(UserPlatformRoleEntity::getUserId, userIds));
        if (bindings.isEmpty()) {
            return Map.of();
        }

        // 批量查询角色
        Map<Long, String> roleKeys = platformRoleMapper.selectBatchIds(bindings.stream()
                        .map(UserPlatformRoleEntity::getRoleId).distinct().toList()).stream()
                .collect(Collectors.toMap(PlatformRoleEntity::getId, PlatformRoleEntity::getRoleKey));
        // 按用户分组，角色去重、排序
        return bindings.stream().filter(binding -> roleKeys.containsKey(binding.getRoleId()))
                .collect(Collectors.groupingBy(UserPlatformRoleEntity::getUserId,
                        Collectors.mapping(binding -> roleKeys.get(binding.getRoleId()),
                                Collectors.collectingAndThen(Collectors.toList(),
                                        values -> values.stream().distinct().sorted().toList()))));
    }

    /**
     * 字符串归一化处理：空白字符串转为null，非空白去除首尾空格
     * @param value 原始字符串
     * @return 处理后字符串
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
