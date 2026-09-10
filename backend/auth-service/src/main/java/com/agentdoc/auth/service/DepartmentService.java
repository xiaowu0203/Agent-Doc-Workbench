package com.agentdoc.auth.service;

import com.agentdoc.auth.enums.DepartmentStatus;
import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.mapper.DepartmentMapper;
import com.agentdoc.auth.mapper.UserMapper;
import com.agentdoc.auth.pojo.dto.DepartmentCreateDTO;
import com.agentdoc.auth.pojo.dto.DepartmentUpdateDTO;
import com.agentdoc.auth.pojo.entity.DepartmentEntity;
import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.pojo.vo.DepartmentMemberCountVO;
import com.agentdoc.auth.pojo.vo.DepartmentVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.agentdoc.auth.constant.PlatformManagementConstant.DEFAULT_DEPARTMENT_SORT_ORDER;
import static com.agentdoc.auth.constant.PlatformManagementConstant.DEPARTMENT_CREATED;
import static com.agentdoc.auth.constant.PlatformManagementConstant.DEPARTMENT_DELETED;
import static com.agentdoc.auth.constant.PlatformManagementConstant.DEPARTMENT_TARGET;
import static com.agentdoc.auth.constant.PlatformManagementConstant.DEPARTMENT_UPDATED;
import static com.agentdoc.auth.constant.PlatformManagementConstant.ROOT_DEPARTMENT_ID;

/**
 * 部门管理服务
 * 提供部门列表查询、详情、新增、修改、删除、循环依赖校验、负责人校验等业务能力
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final PlatformAuditLogService auditLogService;

    /**
     * 查询全部部门
     * 返回扁平列表，前端根据 parentId 自行构建部门树结构
     * 排序规则：按父ID、排序号、创建时间升序
     * 同时组装：成员数量、子部门数量、部门负责人名称
     *
     * @return 部门VO扁平集合
     */
    public List<DepartmentVO> list() {
        // 查询部门列表
        List<DepartmentEntity> departments = departmentMapper.selectList(
                new LambdaQueryWrapper<DepartmentEntity>()
                        .orderByAsc(DepartmentEntity::getParentId)
                        .orderByAsc(DepartmentEntity::getSortOrder)
                        .orderByAsc(DepartmentEntity::getCreatedAt));
        if (departments.isEmpty()) {
            return List.of();
        }

        // 批量查询各部门下成员数量
        Map<Long, Long> memberCounts = userMapper.countByDepartment().stream()
                .collect(Collectors.toMap(DepartmentMemberCountVO::getDepartmentId,
                        DepartmentMemberCountVO::getMemberCount));

        // 统计每个部门的直接子部门数量（排除根部门下的子节点）
        Map<Long, Long> childCounts = departments.stream()
                .filter(department -> department.getParentId() != null
                        && !(ROOT_DEPARTMENT_ID == department.getParentId()))
                .collect(Collectors.groupingBy(DepartmentEntity::getParentId, Collectors.counting()));

        // 批量加载部门负责人昵称/用户名
        Map<Long, String> leaderNames = loadLeaderNames(departments);

        // 组装VO返回
        return departments.stream()
                .map(department -> toVO(department,
                        memberCounts.getOrDefault(department.getId(), 0L),
                        childCounts.getOrDefault(department.getId(), 0L), leaderNames))
                .toList();
    }

    /**
     * 查询单个部门详情
     * @param departmentId 部门ID
     * @return 部门详情VO
     */
    public DepartmentVO detail(Long departmentId) {
        DepartmentEntity department = requireDepartment(departmentId);

        // 查询该部门下用户成员数
        long memberCount = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDepartmentId, departmentId));

        // 查询直接子部门数量
        long childCount = departmentMapper.selectCount(new LambdaQueryWrapper<DepartmentEntity>()
                .eq(DepartmentEntity::getParentId, departmentId));
        return toVO(department, memberCount, childCount, loadLeaderNames(List.of(department)));
    }

    /**
     * 新增部门
     * @param dto 部门新增入参
     * @return 创建后的部门VO
     */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO create(DepartmentCreateDTO dto) {
        // 校验部门编码全局唯一
        requireUniqueCode(dto.code(), null);
        // 规范化父ID，null转为根部门ID
        Long parentId = normalizeParentId(dto.parentId());
        // 校验父部门是否存在
        requireParent(parentId);
        // 校验部门负责人账号有效未禁用
        requireEnabledUser(dto.leaderUserId(), "部门负责人不存在或已禁用");

        DepartmentEntity department = new DepartmentEntity();
        department.setName(dto.name().trim());
        department.setCode(dto.code().trim());
        department.setParentId(parentId);
        department.setLeaderUserId(dto.leaderUserId());

        // 排序号为空则使用默认值
        department.setSortOrder(dto.sortOrder() == null ? DEFAULT_DEPARTMENT_SORT_ORDER : dto.sortOrder());
        // 状态为空默认启用
        department.setStatus(dto.status() == null
                ? DepartmentStatus.ENABLED.getCode() : DepartmentStatus.fromCode(dto.status()).getCode());
        departmentMapper.insert(department);

        // 记录操作审计日志
        auditLogService.record(DEPARTMENT_CREATED, DEPARTMENT_TARGET, department.getId(),
                Map.of("code", department.getCode(), "name", department.getName()));
        return detail(department.getId());
    }

    /**
     * 更新部门信息
     * @param departmentId 待更新部门ID
     * @param dto 部门更新入参
     * @return 更新后的部门VO
     */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO update(Long departmentId, DepartmentUpdateDTO dto) {
        DepartmentEntity department = requireDepartment(departmentId);
        Long parentId = normalizeParentId(dto.parentId());
        requireParent(parentId);
        // 校验不能出现部门层级循环引用
        requireNoCycle(departmentId, parentId);
        // 校验负责人账号有效未禁用
        requireEnabledUser(dto.leaderUserId(), "部门负责人不存在或已禁用");

        department.setName(dto.name().trim());
        department.setParentId(parentId);
        department.setLeaderUserId(dto.leaderUserId());
        department.setSortOrder(dto.sortOrder() == null
                ? DEFAULT_DEPARTMENT_SORT_ORDER : dto.sortOrder());
        // 状态为null则保留原有状态
        department.setStatus(dto.status() == null
                ? department.getStatus() : DepartmentStatus.fromCode(dto.status()).getCode());
        departmentMapper.updateById(department);

        // 记录审计日志
        auditLogService.record(DEPARTMENT_UPDATED, DEPARTMENT_TARGET, departmentId,
                Map.of("code", department.getCode(), "name", department.getName()));
        return detail(departmentId);
    }

    /**
     * 删除部门
     * 前置校验：不能存在子部门、不能存在部门成员，否则禁止删除
     * @param departmentId 部门ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long departmentId) {
        DepartmentEntity department = requireDepartment(departmentId);

        // 存在子部门，禁止删除
        if (departmentMapper.selectCount(new LambdaQueryWrapper<DepartmentEntity>()
                .eq(DepartmentEntity::getParentId, departmentId)) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "部门仍有子部门，不能删除");
        }

        // 部门下存在成员，禁止删除
        if (userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDepartmentId, departmentId)) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "部门仍有成员，不能删除");
        }
        departmentMapper.deleteById(department);

        // 记录审计日志
        auditLogService.record(DEPARTMENT_DELETED, DEPARTMENT_TARGET, departmentId,
                Map.of("code", department.getCode(), "name", department.getName()));
    }

    /**
     * 校验部门存在并且状态为启用
     * @param departmentId 部门ID，null直接返回null
     * @return 启用状态的部门实体
     */
    public DepartmentEntity requireEnabledDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        DepartmentEntity department = requireDepartment(departmentId);
        if (!Integer.valueOf(DepartmentStatus.ENABLED.getCode()).equals(department.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所属部门已禁用");
        }
        return department;
    }

    /**
     * 校验部门必须存在，不存在抛出异常
     * @param departmentId 部门ID
     * @return 部门实体
     */
    public DepartmentEntity requireDepartment(Long departmentId) {
        DepartmentEntity department = departmentMapper.selectById(departmentId);
        if (department == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        return department;
    }

    /**
     * 校验部门编码唯一性
     * @param code 部门编码
     * @param excludedId 更新场景排除自身ID
     */
    private void requireUniqueCode(String code, Long excludedId) {
        LambdaQueryWrapper<DepartmentEntity> wrapper = new LambdaQueryWrapper<DepartmentEntity>()
                .eq(DepartmentEntity::getCode, code.trim());
        if (excludedId != null) {
            wrapper.ne(DepartmentEntity::getId, excludedId);
        }
        if (departmentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "部门编码已存在");
        }
    }

    /**
     * 校验用户是否存在且状态为启用
     * @param userId 用户ID，null跳过校验
     * @param message 校验失败提示文案
     */
    private void requireEnabledUser(Long userId, String message) {
        if (userId == null) {
            return;
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !UserStatus.isEnabled(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    /**
     * 校验父部门合法性
     * 根部门ID无需校验，其他父ID必须存在
     * @param parentId 父部门ID
     */
    private void requireParent(Long parentId) {
        if (!(ROOT_DEPARTMENT_ID == parentId)) {
            requireDepartment(parentId);
        }
    }

    /**
     * 部门层级循环校验
     * 禁止将自己设置为上级，禁止向上追溯形成循环引用
     * @param departmentId 当前部门ID
     * @param parentId 待设置的父部门ID
     */
    private void requireNoCycle(Long departmentId, Long parentId) {
        // 不能把自己设置为父部门
        if (departmentId.equals(parentId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "部门不能作为自己的上级部门");
        }
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (!(ROOT_DEPARTMENT_ID == cursor)) {
            // 出现重复节点代表循环；或者追溯到自身，代表循环
            if (!visited.add(cursor) || departmentId.equals(cursor)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "部门层级不能形成循环");
            }
            cursor = normalizeParentId(requireDepartment(cursor).getParentId());
        }
    }

    /**
     * 规范化父部门ID
     * null 统一转为根部门ID
     * @param parentId 原始父ID
     * @return 处理后的父ID
     */
    private Long normalizeParentId(Long parentId) {
        return parentId == null ? ROOT_DEPARTMENT_ID : parentId;
    }

    /**
     * 批量加载部门负责人名称
     * 优先取昵称，昵称为空取用户名
     * @param departments 部门集合
     * @return key:userId value:负责人名称
     */
    private Map<Long, String> loadLeaderNames(List<DepartmentEntity> departments) {
        List<Long> leaderIds = departments.stream().map(DepartmentEntity::getLeaderUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (leaderIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(leaderIds).stream()
                .collect(Collectors.toMap(UserEntity::getId,
                        user -> user.getNickname() == null || user.getNickname().isBlank()
                                ? user.getUsername() : user.getNickname(),
                        (left, right) -> left));
    }

    /**
     * Entity转VO
     * @param department 部门实体
     * @param memberCount 部门成员数量
     * @param childCount 直接子部门数量
     * @param leaderNames 负责人名称映射
     * @return 部门VO
     */
    private DepartmentVO toVO(DepartmentEntity department, long memberCount, long childCount,
                              Map<Long, String> leaderNames) {
        String leaderName = department.getLeaderUserId() == null
                ? null : leaderNames.get(department.getLeaderUserId());
        return new DepartmentVO(department.getId(), department.getParentId(), department.getName(),
                department.getCode(), department.getLeaderUserId(),
                leaderName, memberCount, childCount,
                department.getSortOrder(), department.getStatus(), department.getCreatedAt(),
                department.getUpdatedAt());
    }
}
