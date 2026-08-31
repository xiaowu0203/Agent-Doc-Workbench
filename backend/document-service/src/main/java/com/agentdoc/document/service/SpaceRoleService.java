package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.document.constant.DefaultSpaceRoleConstant;
import com.agentdoc.document.mapper.MemberMapper;
import com.agentdoc.document.mapper.PermissionMapper;
import com.agentdoc.document.mapper.SpaceRoleMapper;
import com.agentdoc.document.mapper.SpaceRolePermissionMapper;
import com.agentdoc.document.pojo.dto.RolePermissionReplaceDTO;
import com.agentdoc.document.pojo.dto.SpaceRoleCreateDTO;
import com.agentdoc.document.pojo.dto.SpaceRoleUpdateDTO;
import com.agentdoc.document.pojo.entity.MemberEntity;
import com.agentdoc.document.pojo.entity.PermissionEntity;
import com.agentdoc.document.pojo.entity.SpaceRoleEntity;
import com.agentdoc.document.pojo.entity.SpaceRolePermissionEntity;
import com.agentdoc.document.pojo.vo.PermissionVO;
import com.agentdoc.document.pojo.vo.SpaceRoleVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.agentdoc.common.constant.SpacePermissionConstant.AGENT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.AUDIT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_APPROVE;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_MERGE;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.CHANGE_REQUEST_SUBMIT;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_CREATE;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_EDIT;
import static com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.MCP_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.MEMBER_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.ROLE_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.SKILL_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.SPACE_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_CREATE;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_READ;
import static com.agentdoc.common.constant.SpacePermissionConstant.TASK_TERMINATE;
import static com.agentdoc.common.constant.SpacePermissionConstant.USAGE_READ;
import static com.agentdoc.document.constant.DefaultSpaceRoleConstant.EDITOR;
import static com.agentdoc.document.constant.DefaultSpaceRoleConstant.OWNER;
import static com.agentdoc.document.constant.DefaultSpaceRoleConstant.VIEWER;

/**
 * 空间角色服务
 * <p>
 * 管理空间下角色生命周期：创建、查询、修改、删除；维护角色‑权限绑定关系。
 * </p>
 * <p>
 * 内置系统默认角色：OWNER 为受保护角色，不允许修改、删除；EDITOR、VIEWER 允许在空间内调整。
 * <ul>
 *     <li>initializeDefaultRoles：新建空间时初始化三套系统角色</li>
 *     <li>create/update/replacePermissions/delete：自定义角色CRUD与权限覆盖</li>
 *     <li>requireRole / requireMutableRole：角色存在性与可变性校验工具方法</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SpaceRoleService {

    private final PermissionMapper permissionMapper;
    private final SpaceRoleMapper spaceRoleMapper;
    private final SpaceRolePermissionMapper rolePermissionMapper;
    private final MemberMapper memberMapper;

    /**
     * 获取系统全部可用权限列表
     *
     * @return 权限VO有序列表，按sortOrder排序
     * @throws BusinessException 未登录
     */
    public List<PermissionVO> listPermissions() {
        // 校验当前用户ID
        AuthUtils.getUserIdOrException();
        return permissionMapper.selectList(new LambdaQueryWrapper<PermissionEntity>()
                        .orderByAsc(PermissionEntity::getSortOrder))
                .stream()
                .map(PermissionVO::from)
                .toList();
    }

    /**
     * 查询指定空间下全部角色列表，附带每个角色绑定的权限编码
     *
     * @param spaceId 空间ID
     * @return 角色VO列表；受保护角色优先展示，再按创建时间升序
     */
    public List<SpaceRoleVO> listRoles(Long spaceId) {
        List<SpaceRoleEntity> roles = spaceRoleMapper.selectList(
                new LambdaQueryWrapper<SpaceRoleEntity>()
                        .eq(SpaceRoleEntity::getSpaceId, spaceId)
                        .orderByDesc(SpaceRoleEntity::getProtectedRole)
                        .orderByAsc(SpaceRoleEntity::getCreatedAt));
        // 批量查询所有角色对应的权限
        Map<Long, List<String>> permissionMap = listPermissionMap(
                roles.stream().map(SpaceRoleEntity::getId).toList());
        return roles.stream()
                .map(role -> SpaceRoleVO.from(role,
                        permissionMap.getOrDefault(role.getId(), List.of())))
                .toList();
    }

    /**
     * 获取单个空间角色详情，返回角色信息+绑定权限编码
     *
     * @param spaceId 空间ID
     * @param roleId  角色ID
     * @return 角色VO
     * @throws BusinessException 角色不存在或不属于该空间
     */
    public SpaceRoleVO detail(Long spaceId, Long roleId) {
        SpaceRoleEntity role = requireRole(spaceId, roleId);
        return SpaceRoleVO.from(role, listPermissionCodes(roleId));
    }

    /**
     * 创建自定义空间角色，并绑定权限集合
     *
     * @param spaceId 空间ID
     * @param dto     创建角色入参
     * @return 创建完成的角色VO
     * @throws BusinessException 角色标识roleKey重复；包含非法/重复权限码
     */
    @Transactional(rollbackFor = Exception.class)
    public SpaceRoleVO create(Long spaceId, SpaceRoleCreateDTO dto) {
        // 校验同空间下roleKey唯一
        Long duplicate = spaceRoleMapper.selectCount(new LambdaQueryWrapper<SpaceRoleEntity>()
                .eq(SpaceRoleEntity::getSpaceId, spaceId)
                .eq(SpaceRoleEntity::getRoleKey, dto.roleKey()));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色标识已存在");
        }
        // 校验权限码合法性，去重、过滤未知权限
        List<String> permissions = validatePermissionCodes(dto.permissionCodes());
        SpaceRoleEntity role = new SpaceRoleEntity();
        role.setSpaceId(spaceId);
        role.setRoleKey(dto.roleKey());
        role.setDisplayName(dto.displayName());
        role.setDescription(dto.description());
        role.setProtectedRole(false);
        role.setCreatedBy(AuthUtils.getUserIdOrException());
        spaceRoleMapper.insert(role);
        // 批量插入角色权限关联
        insertPermissions(role.getId(), permissions);
        return SpaceRoleVO.from(role, permissions);
    }

    /**
     * 更新空间角色基础信息（展示名、描述），OWNER 受保护角色除外
     *
     * @param spaceId 空间ID
     * @param roleId  角色ID
     * @param dto     更新入参
     * @return 更新后角色VO
     * @throws BusinessException 角色不存在；角色为受保护 OWNER 时不可修改
     */
    public SpaceRoleVO update(Long spaceId, Long roleId, SpaceRoleUpdateDTO dto) {
        SpaceRoleEntity role = requireMutableRole(spaceId, roleId);
        role.setDisplayName(dto.displayName());
        role.setDescription(dto.description());
        spaceRoleMapper.updateById(role);
        return SpaceRoleVO.from(role, listPermissionCodes(roleId));
    }

    /**
     * 全量替换空间角色权限集合：先删除旧权限，再插入新权限
     *
     * @param spaceId 空间ID
     * @param roleId  角色ID
     * @param dto     新权限集合
     * @return 更新后角色VO
     * @throws BusinessException 角色不存在；受保护 OWNER 禁止修改；权限码非法重复
     */
    @Transactional(rollbackFor = Exception.class)
    public SpaceRoleVO replacePermissions(Long spaceId, Long roleId, RolePermissionReplaceDTO dto) {
        SpaceRoleEntity role = requireMutableRole(spaceId, roleId);
        List<String> permissions = validatePermissionCodes(dto.permissionCodes());
        // 删除该角色全部旧权限关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<SpaceRolePermissionEntity>()
                .eq(SpaceRolePermissionEntity::getRoleId, roleId));
        // 写入全新权限集合
        insertPermissions(roleId, permissions);
        return SpaceRoleVO.from(role, permissions);
    }

    /**
     * 删除空间角色
     * <p>删除前校验：不能有成员还绑定该角色；同时清理角色‑权限关联数据</p>
     *
     * @param spaceId 空间ID
     * @param roleId  角色ID
     * @throws BusinessException 角色不存在；受保护 OWNER；仍存在成员绑定该角色
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long spaceId, Long roleId) {
        SpaceRoleEntity role = requireMutableRole(spaceId, roleId);
        // 校验是否还有空间成员使用该角色
        Long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<MemberEntity>()
                .eq(MemberEntity::getRoleId, roleId));
        if (memberCount != null && memberCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色仍有成员绑定，不能删除");
        }
        // 删除角色权限关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<SpaceRolePermissionEntity>()
                .eq(SpaceRolePermissionEntity::getRoleId, roleId));
        // 删除角色本体
        spaceRoleMapper.deleteById(role);
    }

    /**
     * 新建空间时初始化系统默认角色：所有者、编辑者、观察者。
     * <p>OWNER 分配全部权限并标记为受保护；EDITOR、VIEWER 分配预设权限集合且允许调整。</p>
     * @param spaceId    新建空间ID
     * @param creatorId  空间创建人用户ID
     * @return 所有者角色实体
     */
    public SpaceRoleEntity initializeDefaultRoles(Long spaceId, Long creatorId) {
        // 查询系统定义全部权限，分配给所有者
        List<String> allPermissions = permissionMapper.selectList(
                        new LambdaQueryWrapper<PermissionEntity>()
                                .orderByAsc(PermissionEntity::getSortOrder))
                .stream()
                .map(PermissionEntity::getCode)
                .toList();
        SpaceRoleEntity owner = createDefaultRole(
                spaceId, OWNER, "所有者", "拥有空间全部权限", creatorId, true, allPermissions);
        createDefaultRole(spaceId, EDITOR, "编辑者", "可编辑文档、创建任务和审批变更",
                creatorId, false, DefaultSpaceRoleConstant.EDITOR_PERMISSIONS);
        createDefaultRole(spaceId, VIEWER, "观察者", "只读查看空间资源",
                creatorId, false, DefaultSpaceRoleConstant.VIEWER_PERMISSIONS);
        return owner;
    }

    /**
     * 校验角色存在并且属于目标空间
     *
     * @param spaceId 空间ID
     * @param roleId  角色ID
     * @return 角色实体
     * @throws BusinessException NOT_FOUND 角色不存在或不属于该空间
     */
    public SpaceRoleEntity requireRole(Long spaceId, Long roleId) {
        SpaceRoleEntity role = spaceRoleMapper.selectById(roleId);
        if (role == null || !spaceId.equals(role.getSpaceId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "空间角色不存在");
        }
        return role;
    }

    /**
     * 根据roleKey获取角色，校验存在性
     *
     * @param spaceId 空间ID
     * @param roleKey 角色唯一标识
     * @return 角色实体
     * @throws BusinessException NOT_FOUND
     */
    public SpaceRoleEntity requireRoleByKey(Long spaceId, String roleKey) {
        SpaceRoleEntity role = spaceRoleMapper.selectOne(new LambdaQueryWrapper<SpaceRoleEntity>()
                .eq(SpaceRoleEntity::getSpaceId, spaceId)
                .eq(SpaceRoleEntity::getRoleKey, roleKey));
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "空间角色不存在");
        }
        return role;
    }

    /**
     * 校验角色：存在、属于该空间，并且不是受保护 OWNER，允许修改/删除
     *
     * @param spaceId 空间ID
     * @param roleId  角色ID
     * @return 可变更角色实体
     * @throws BusinessException 角色不存在；受保护 OWNER 禁止变更
     */
    private SpaceRoleEntity requireMutableRole(Long spaceId, Long roleId) {
        SpaceRoleEntity role = requireRole(spaceId, roleId);
        if (Boolean.TRUE.equals(role.getProtectedRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "受保护默认角色不可修改");
        }
        return role;
    }

    /**
     * 创建系统内置角色，用于空间初始化
     *
     * @param spaceId      空间ID
     * @param roleKey      角色唯一key
     * @param displayName  展示名称
     * @param description  描述
     * @param creatorId    创建人ID
     * @param permissions  绑定权限编码集合
     * @return 新建角色实体
     */
    private SpaceRoleEntity createDefaultRole(Long spaceId, String roleKey, String displayName,
                                               String description, Long creatorId,
                                               boolean protectedRole,
                                               List<String> permissions) {
        SpaceRoleEntity role = new SpaceRoleEntity();
        role.setSpaceId(spaceId);
        role.setRoleKey(roleKey);
        role.setDisplayName(displayName);
        role.setDescription(description);
        role.setProtectedRole(protectedRole);
        role.setCreatedBy(creatorId);
        spaceRoleMapper.insert(role);
        insertPermissions(role.getId(), permissions);
        return role;
    }

    /**
     * 校验权限码集合：不能重复；必须全部为系统内已定义权限；返回按sortOrder排序后的权限编码
     *
     * @param requestedCodes 待校验权限编码集合
     * @return 校验通过、有序的权限编码列表
     * @throws BusinessException BAD_REQUEST 权限重复或存在未知权限码
     */
    private List<String> validatePermissionCodes(Collection<String> requestedCodes) {
        Set<String> codes = requestedCodes.stream().collect(Collectors.toSet());
        if (codes.size() != requestedCodes.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "权限标识符不能重复");
        }
        // 批量查询校验权限码真实存在
        Map<String, PermissionEntity> permissions = permissionMapper.selectBatchIds(codes).stream()
                .collect(Collectors.toMap(PermissionEntity::getCode, Function.identity()));
        if (permissions.size() != codes.size()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "包含未知权限标识符");
        }
        // 按照权限定义sortOrder重新排序输出
        return permissions.values().stream()
                .sorted((left, right) -> left.getSortOrder().compareTo(right.getSortOrder()))
                .map(PermissionEntity::getCode)
                .toList();
    }

    /**
     * 为指定角色批量插入角色‑权限关联记录
     *
     * @param roleId         角色ID
     * @param permissionCodes 权限编码集合
     */
    private void insertPermissions(Long roleId, Collection<String> permissionCodes) {
        for (String permissionCode : permissionCodes) {
            rolePermissionMapper.insert(SpaceRolePermissionEntity.of(roleId, permissionCode));
        }
    }

    /**
     * 查询单个角色绑定的全部权限编码
     *
     * @param roleId 角色ID
     * @return 权限编码有序列表
     */
    private List<String> listPermissionCodes(Long roleId) {
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SpaceRolePermissionEntity>()
                        .eq(SpaceRolePermissionEntity::getRoleId, roleId)
                        .orderByAsc(SpaceRolePermissionEntity::getPermissionCode))
                .stream()
                .map(SpaceRolePermissionEntity::getPermissionCode)
                .toList();
    }

    /**
     * 批量查询多个角色对应的权限，返回 roleId → 权限列表映射，避免循环查询数据库
     *
     * @param roleIds 角色ID集合
     * @return Map<角色ID,权限编码列表>；入参空返回空Map
     */
    private Map<Long, List<String>> listPermissionMap(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<SpaceRolePermissionEntity>()
                        .in(SpaceRolePermissionEntity::getRoleId, roleIds)
                        .orderByAsc(SpaceRolePermissionEntity::getPermissionCode))
                .stream()
                .collect(Collectors.groupingBy(SpaceRolePermissionEntity::getRoleId,
                        Collectors.mapping(SpaceRolePermissionEntity::getPermissionCode,
                                Collectors.toList())));
    }
}
