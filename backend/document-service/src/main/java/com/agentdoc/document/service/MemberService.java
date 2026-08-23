package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.document.enums.SpaceRole;
import com.agentdoc.document.mapper.MemberMapper;
import com.agentdoc.document.pojo.dto.MemberAddDTO;
import com.agentdoc.document.pojo.dto.MemberRoleUpdateDTO;
import com.agentdoc.document.pojo.entity.MemberEntity;
import com.agentdoc.document.pojo.vo.MemberVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 空间成员服务
 * 能力：空间成员新增、查询成员列表、修改成员角色、移除成员
 * 权限约束：成员新增、改角色、移除等管理操作，必须拥有空间 OWNER 所有者权限；
 * 业务保护规则：空间必须至少保留一名 OWNER，不允许全部所有者被降级或移除。
 */
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final SpacePermissionService permissionService;

    /**
     * 添加空间成员
     * 权限：需要空间 OWNER 所有者权限；不能重复添加同一用户
     *
     * @param spaceId 空间ID
     * @param dto 成员添加请求，包含被添加用户ID、分配角色
     * @return 新增完成的成员VO视图
     */
    public MemberVO add(Long spaceId, MemberAddDTO dto) {
        // 校验当前操作用户为空间所有者
        permissionService.requireRole(spaceId, SpaceRole.OWNER);
        // 查询该用户是否已经是本空间成员
        MemberEntity exist = findMember(spaceId, dto.userId());
        if (exist != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该用户已是空间成员");
        }
        // DTO转数据库实体，插入成员记录
        MemberEntity member = dto.toEntity(spaceId);
        memberMapper.insert(member);
        return member.toVO();
    }

    /**
     * 获取空间全部成员列表
     * 权限：只要是该空间成员即可查看；排序：角色升序，加入时间倒序
     *
     * @param spaceId 空间ID
     * @return 空间成员VO集合
     */
    public List<MemberVO> list(Long spaceId) {
        // 校验当前用户属于该空间成员
        permissionService.requireMember(spaceId);
        return memberMapper.selectList(new LambdaQueryWrapper<MemberEntity>()
                        .eq(MemberEntity::getSpaceId, spaceId)
                        .orderByAsc(MemberEntity::getRole)
                        .orderByDesc(MemberEntity::getCreatedAt))
                .stream()
                .map(MemberEntity::toVO)
                .toList();
    }

    /**
     * 修改空间成员角色
     * 权限：需要OWNER所有者权限；业务保护：空间不能没有OWNER，唯一OWNER不允许被降级
     *
     * @param spaceId 空间ID
     * @param userId 待修改角色的目标用户ID
     * @param dto 携带要变更的新角色
     * @return 更新后的成员VO视图
     */
    public MemberVO changeRole(Long spaceId, Long userId, MemberRoleUpdateDTO dto) {
        permissionService.requireRole(spaceId, SpaceRole.OWNER);
        // 校验用户是本空间有效成员
        MemberEntity member = requireMember(spaceId, userId);
        SpaceRole newRole = dto.role();
        // 业务防护：当前用户是OWNER，要降级，且空间仅有这一个OWNER → 禁止降级
        if (member.getRole() == SpaceRole.OWNER.getCode()
                && newRole != SpaceRole.OWNER
                && countOwners(spaceId) <= 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "空间至少保留一名所有者");
        }
        // 更新角色编码到数据库
        member.setRole(newRole.getCode());
        memberMapper.updateById(member);
        return member.toVO();
    }

    /**
     * 移除空间成员
     * 权限：需要OWNER所有者权限；业务保护：不允许移除空间最后一名OWNER
     *
     * @param spaceId 空间ID
     * @param userId 待移除的用户ID
     */
    public void remove(Long spaceId, Long userId) {
        permissionService.requireRole(spaceId, SpaceRole.OWNER);
        // 校验该用户是本空间成员
        MemberEntity member = requireMember(spaceId, userId);
        // 禁止移除空间仅剩的所有者
        if (member.getRole() == SpaceRole.OWNER.getCode() && countOwners(spaceId) <= 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "空间至少保留一名所有者");
        }
        // 删除该条成员关系记录
        memberMapper.deleteById(member.getId());
    }

    /**
     * 根据空间ID+用户ID获取成员实体，不存在抛出404业务异常
     *
     * @param spaceId 空间ID
     * @param userId 用户ID
     * @return 成员数据库实体
     */
    private MemberEntity requireMember(Long spaceId, Long userId) {
        MemberEntity member = findMember(spaceId, userId);
        if (member == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该用户不是空间成员");
        }
        return member;
    }

    /**
     * 根据空间ID+用户ID查询成员记录，允许返回null
     *
     * @param spaceId 空间ID
     * @param userId 用户ID
     * @return 成员实体，未加入则返回null
     */
    private MemberEntity findMember(Long spaceId, Long userId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<MemberEntity>()
                .eq(MemberEntity::getSpaceId, spaceId)
                .eq(MemberEntity::getUserId, userId));
    }

    /**
     * 统计该空间内OWNER所有者成员总数量
     *
     * @param spaceId 空间ID
     * @return owner成员数量
     */
    private long countOwners(Long spaceId) {
        return memberMapper.selectCount(new LambdaQueryWrapper<MemberEntity>()
                .eq(MemberEntity::getSpaceId, spaceId)
                .eq(MemberEntity::getRole, SpaceRole.OWNER.getCode()));
    }
}
