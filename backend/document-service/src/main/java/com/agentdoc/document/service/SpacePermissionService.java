package com.agentdoc.document.service;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.constant.PlatformRoleConstant;
import com.agentdoc.common.context.TaskCapabilityContext;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.feign.AuthFeign;
import com.agentdoc.common.feign.TaskFeign;
import com.agentdoc.common.security.TaskCapabilityVerifier;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.document.mapper.MemberMapper;
import com.agentdoc.document.mapper.PermissionMapper;
import com.agentdoc.document.mapper.SpaceRoleMapper;
import com.agentdoc.document.mapper.SpaceRolePermissionMapper;
import com.agentdoc.document.pojo.entity.MemberEntity;
import com.agentdoc.document.pojo.entity.PermissionEntity;
import com.agentdoc.document.pojo.entity.SpaceRoleEntity;
import com.agentdoc.document.pojo.entity.SpaceRolePermissionEntity;
import com.agentdoc.document.pojo.vo.EffectivePermissionVO;
import com.agentdoc.document.pojo.vo.SpaceRoleSummaryVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;
import static com.agentdoc.document.constant.DefaultSpaceRoleConstant.OWNER;

/**
 * 空间权限校验服务
 * <p>
 * 负责空间维度的权限判定：用户‑空间‑角色‑权限模型；
 * 支持普通登录用户、平台超级管理员特殊权限；
 * 同时提供 Agent 任务能力令牌鉴权能力，用于Agent执行动作的安全校验。
 * </p>
 * <p>
 * 使用说明：
 * <ul>
 *     <li>{@link #hasPermission(Long, String)}：判断是否拥有权限，返回布尔值，不抛异常</li>
 *     <li>{@link #requirePermission(Long, String)}：校验权限，不通过直接抛出FORBIDDEN业务异常</li>
 *     <li>{@link #requireAgentCapability(Long, Long, String)}：Agent任务调用专用鉴权，校验任务令牌与远程任务能力范围</li>
 * </ul>
 * </p>
 */
@Component("SpacePermission")
@Service
public class SpacePermissionService {

    private final MemberMapper memberMapper;
    private final SpaceRoleMapper spaceRoleMapper;
    private final SpaceRolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final AuthFeign authFeign;
    private final TaskFeign taskFeign;
    private final TaskCapabilityVerifier taskCapabilityVerifier;
    private final HttpServletRequest request;

    @Autowired
    public SpacePermissionService(MemberMapper memberMapper,
                                  SpaceRoleMapper spaceRoleMapper,
                                  SpaceRolePermissionMapper rolePermissionMapper,
                                  PermissionMapper permissionMapper,
                                  AuthFeign authFeign,
                                  TaskFeign taskFeign,
                                  TaskCapabilityVerifier taskCapabilityVerifier,
                                  HttpServletRequest request) {
        this.memberMapper = memberMapper;
        this.spaceRoleMapper = spaceRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.authFeign = authFeign;
        this.taskFeign = taskFeign;
        this.taskCapabilityVerifier = taskCapabilityVerifier;
        this.request = request;
    }

    /**
     * 判断用户在指定空间下是否具备某权限
     *
     * @param spaceId        目标空间ID
     * @param permissionCode 权限编码
     * @return true-拥有权限；false-无权限 / 参数为空 / 非空间成员
     */
    public boolean hasPermission(Long spaceId, String permissionCode) {
        Long userId = AuthUtils.getUserId();
        // 用户ID、空间ID、权限码任一为空，直接判定无权限
        if (userId == null || spaceId == null || permissionCode == null) {
            return false;
        }
        // 平台超级管理员：对于配置的跨空间读权限直接放行，允许读取任意空间资源
        if (isPlatformSuperAdmin() && PlatformRoleConstant.PLATFORM_CROSS_SPACE_READ_PERMISSIONS.contains(permissionCode)) {
            return true;
        }
        // 查询该用户在该空间下的成员记录
        MemberEntity member = findMember(spaceId, userId);
        // 成员存在，校验该成员绑定角色是否拥有目标权限
        return member != null && rolePermissionMapper.selectCount(
                new LambdaQueryWrapper<SpaceRolePermissionEntity>()
                        .eq(SpaceRolePermissionEntity::getRoleId, member.getRoleId())
                        .eq(SpaceRolePermissionEntity::getPermissionCode, permissionCode)) > 0;
    }

    /**
     * 要求必须拥有指定空间权限，不满足抛出FORBIDDEN异常
     *
     * @param spaceId        目标空间ID
     * @param permissionCode 权限编码
     * @throws BusinessException 未登录 或 不具备权限
     */
    public void requirePermission(Long spaceId, String permissionCode) {
        // 校验当前上下文存在登录用户ID
        requireUserId();
        if (!hasPermission(spaceId, permissionCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "缺少空间权限：" + permissionCode);
        }
    }

    /**
     * 获取用户在指定空间下的角色信息
     *
     * @param spaceId 目标空间ID
     * @param userId  用户ID
     * @return 角色实体；null表示用户不是该空间成员
     */
    public SpaceRoleEntity getMemberRole(Long spaceId, Long userId) {
        MemberEntity member = findMember(spaceId, userId);
        return member == null ? null : spaceRoleMapper.selectById(member.getRoleId());
    }

    /**
     * 要求当前用户必须是目标空间的成员，返回成员对应的角色
     *
     * @param spaceId 目标空间ID
     * @return 空间角色实体
     * @throws BusinessException 未登录 / 不是该空间成员
     */
    public SpaceRoleEntity requireMemberRole(Long spaceId) {
        SpaceRoleEntity role = getMemberRole(spaceId, requireUserId());
        if (role == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不是该空间成员");
        }
        return role;
    }

    /**
     * 判断当前登录用户是否为平台超级管理员
     * <p>通过authFeign远程鉴权；feign调用异常/降级场景返回false，不做权限放行</p>
     *
     * @return true 平台超级管理员；false 非平台超管或者调用异常
     */
    public boolean isPlatformSuperAdmin() {
        if (!AuthUtils.hasPlatformRole(SUPER_ADMIN) || authFeign == null) {
            return false;
        }
        try {
            Result<Void> result = authFeign.checkPlatformRole(SUPER_ADMIN);
            return result != null && result.code() == ErrorCode.SUCCESS.getCode();
        } catch (RuntimeException exception) {
            // feign远程调用异常，安全策略：拒绝，不授予超管权限
            return false;
        }
    }

    /**
     * 要求当前用户必须是空间OWNER角色
     *
     * @param spaceId 目标空间ID
     * @throws BusinessException 未登录 / 非空间成员 / 角色不是空间所有者
     */
    public void requireOwner(Long spaceId) {
        requireUserId();
        SpaceRoleEntity role = requireMemberRole(spaceId);
        if (!OWNER.equals(role.getRoleKey())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要空间所有者权限");
        }
    }

    /**
     * 获取用户在指定空间下的全部有效权限集合
     * <p>平台超级管理员会叠加【平台跨空间读权限】+ 自身在该空间角色权限；普通用户返回空间角色绑定权限</p>
     *
     * @param spaceId 目标空间ID
     * @return 有效权限VO，包含权限列表、角色摘要、是否平台超管标记
     * @throws BusinessException 未登录；非空间成员(非平台超管)
     */
    public EffectivePermissionVO getEffectivePermissions(Long spaceId) {
        requireUserId();
        if (isPlatformSuperAdmin()) {
            // 平台超管：先加载平台允许跨空间读取的权限
            Set<String> effectivePermissions = new LinkedHashSet<>(permissionMapper.selectList(
                            new LambdaQueryWrapper<PermissionEntity>()
                                    .in(PermissionEntity::getCode, PlatformRoleConstant.PLATFORM_CROSS_SPACE_READ_PERMISSIONS)
                                    .orderByAsc(PermissionEntity::getSortOrder))
                    .stream()
                    .map(PermissionEntity::getCode)
                    .toList());
            // 如果该用户同时是本空间成员，叠加该空间角色本身权限
            SpaceRoleEntity memberRole = getMemberRole(spaceId, AuthUtils.getUserId());
            if (memberRole != null) {
                effectivePermissions.addAll(loadRolePermissions(memberRole.getId()));
            }
            // 普通空间成员：只返回角色绑定权限
            return new EffectivePermissionVO(spaceId, true,
                    memberRole == null ? null : SpaceRoleSummaryVO.from(memberRole),
                    new ArrayList<>(effectivePermissions));
        }
        SpaceRoleEntity role = requireMemberRole(spaceId);
        return new EffectivePermissionVO(spaceId, false, SpaceRoleSummaryVO.from(role),
                loadRolePermissions(role.getId()));
    }

    /**
     * 根据角色ID查询该角色拥有的全部权限编码列表
     *
     * @param roleId 空间角色ID
     * @return 权限编码有序列表
     */
    private List<String> loadRolePermissions(Long roleId) {
        return rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<SpaceRolePermissionEntity>()
                                .eq(SpaceRolePermissionEntity::getRoleId, roleId)
                                .orderByAsc(SpaceRolePermissionEntity::getPermissionCode))
                .stream()
                .map(SpaceRolePermissionEntity::getPermissionCode)
                .toList();
    }

    /**
     * 校验 Agent 任务能力令牌，并回查 task‑service 当前任务范围。
     * <p>
     * 用于 Agent‑Doc‑Workbench Agent 执行动作时鉴权：
     * 1. 从请求头获取任务能力令牌 X‑TASK‑CAPABILITY
     * 2. 本地上下文校验 Agent 身份、spaceId、documentId、操作权限、taskId
     * 3. 校验令牌签名合法性
     * 4. Feign远程调用 task‑service，回校该任务的实际可用能力范围
     * 5. 校验通过将令牌放入线程上下文，执行完毕清理上下文
     * </p>
     * @param spaceId 空间ID，待校验访问的目标空间
     * @param documentId 文档ID，待校验访问的目标文档
     * @param action Agent待执行动作标识，用于校验动作权限
     * @throws BusinessException 校验不通过抛出业务异常：无权限、未配置校验组件、令牌非法、远程任务校验失败等
     */
    public void requireAgentCapability(Long spaceId, Long documentId, String action) {
        // 从HTTP请求头获取【X‑TASK‑CAPABILITY】任务能力令牌
        String token = request == null ? null : request.getHeader(HeaderConstants.X_TASK_CAPABILITY);

        /**
         * 本地前置合法性校验：
         * 1. 当前上下文必须为Agent调用；
         * 2. 请求头必须携带任务能力令牌；
         * 3. 传入的spaceId、documentId必须与令牌上下文内一致，防止越权访问其他空间/文档；
         * 4. Agent具备该action动作执行权限；
         * 5. 上下文必须携带taskId任务编号；
         * 任意条件不满足，直接抛出无权访问异常
         */
        if (!AuthUtils.isAgent()
                || token == null
                || !spaceId.equals(AuthUtils.getSpaceId())
                || !documentId.equals(AuthUtils.getDocumentId())
                || !AuthUtils.hasAgentAction(action)
                || AuthUtils.getTaskId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent 无权访问该文档");
        }

        // 校验依赖组件是否已注入，缺少校验器/Feign客户端直接拒绝
        if (taskCapabilityVerifier == null || taskFeign == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Agent 能力校验未配置");
        }

        // 本地验签：校验X‑TASK‑CAPABILITY令牌签名、有效期合法性
        taskCapabilityVerifier.verify(token);
        // 将任务能力令牌设置到当前线程上下文，供后续业务逻辑取用
        TaskCapabilityContext.set(token);
        try {
            // Feign远程调用task‑service，回查该taskId对应的任务实际能力范围，做二次强校验
            Result<Void> result = taskFeign.checkTaskCapability(AuthUtils.getTaskId());
            // 远程调用返回非成功状态，抛出业务异常
            if (result == null || result.code() != ErrorCode.SUCCESS.getCode()) {
                throw new BusinessException(result == null ? ErrorCode.FORBIDDEN.getCode() : result.code(),
                        result == null ? "任务能力校验失败" : result.message());
            }
        } finally {
            // 无论正常/异常，强制清空当前线程令牌上下文，防止线程池线程复用造成上下文泄露
            TaskCapabilityContext.clear();
        }
    }

    /**
     * 取当前人类用户 ID，Agent Capability 不得解释为用户身份。
     * @return 用户 ID
     * @throws BusinessException 未登录，{@link ErrorCode#UNAUTHORIZED}
     */
    public Long requireUserId() {
        return AuthUtils.getUserIdOrException();
    }

    /**
     * 查询用户在指定空间下的成员记录
     *
     * @param spaceId 空间ID
     * @param userId  用户ID
     * @return 成员实体；null代表用户不是该空间成员
     */
    private MemberEntity findMember(Long spaceId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<MemberEntity>()
                .eq(MemberEntity::getSpaceId, spaceId)
                .eq(MemberEntity::getUserId, userId));
    }
}
