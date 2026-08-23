package com.agentdoc.document.service;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.document.enums.SpaceRole;
import com.agentdoc.document.enums.SpaceStatus;
import com.agentdoc.document.mapper.MemberMapper;
import com.agentdoc.document.mapper.SpaceMapper;
import com.agentdoc.document.pojo.dto.SpaceCreateDTO;
import com.agentdoc.document.pojo.dto.SpaceUpdateDTO;
import com.agentdoc.document.pojo.entity.MemberEntity;
import com.agentdoc.document.pojo.entity.SpaceEntity;
import com.agentdoc.document.pojo.vo.SpaceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 空间服务
 * 能力：空间创建、查询、信息更新、逻辑删除；
 * 业务规则：创建空间的用户会自动作为OWNER写入空间成员表；
 * 删除空间会逻辑删除空间本身，并清空该空间全部成员关联记录。
 */
@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceMapper spaceMapper;
    private final MemberMapper memberMapper;
    private final SpacePermissionService permissionService;

    /**
     * 创建空间
     * 事务保障：同时写入空间主记录 + 创建者OWNER成员记录，任一异常全部回滚。
     *
     * @param dto 空间创建请求DTO
     * @return 空间视图VO，携带当前用户OWNER角色
     */
    @Transactional(rollbackFor = Exception.class)
    public SpaceVO create(SpaceCreateDTO dto) {
        // 获取当前登录用户ID，未登录抛异常
        Long userId = permissionService.requireUserId();
        // DTO转换为空间数据库实体，设置创建人
        SpaceEntity space = dto.toEntity(userId);
        // 设置空间状态为正常可用
        space.setStatus(SpaceStatus.NORMAL.getCode());
        // 插入空间记录
        spaceMapper.insert(space);

        // 创建者自动成为该空间OWNER所有者，写入成员表
        MemberEntity member = new MemberEntity();
        member.setSpaceId(space.getId());
        member.setUserId(userId);
        member.setRole(SpaceRole.OWNER.getCode());
        memberMapper.insert(member);
        return space.toVO(SpaceRole.OWNER);
    }

    /**
     * 查询我参与的全部空间列表
     * 逻辑：先查当前用户所有成员关系，再关联查询空间信息；按空间创建时间倒序；返回结果携带用户在每个空间的角色。
     *
     * @return 用户参与的空间VO集合
     */
    public List<SpaceVO> listMySpaces() {
        Long userId = permissionService.requireUserId();
        // 查询当前用户所有的空间成员关系记录
        List<MemberEntity> members = memberMapper.selectList(new LambdaQueryWrapper<MemberEntity>()
                .eq(MemberEntity::getUserId, userId));
        // 用户未加入任何空间，直接返回空集合
        if (members.isEmpty()) {
            return List.of();
        }
        // 提取关联的空间ID集合，做去重
        List<Long> spaceIds = members.stream().map(MemberEntity::getSpaceId).distinct().toList();
        // key:空间ID，value:当前用户在该空间对应的角色
        Map<Long, SpaceRole> roleMap = members.stream()
                .collect(Collectors.toMap(MemberEntity::getSpaceId,
                        m -> SpaceRole.fromCode(m.getRole()), (a, b) -> a));
        // 批量查询空间，按创建时间倒序排列
        return spaceMapper.selectList(new LambdaQueryWrapper<SpaceEntity>()
                        .in(SpaceEntity::getId, spaceIds)
                        .orderByDesc(SpaceEntity::getCreatedAt))
                .stream()
                .map(space -> space.toVO(roleMap.get(space.getId())))
                .toList();
    }

    /**
     * 获取空间详情
     * 权限：当前用户必须是该空间成员；返回VO携带当前用户在本空间的角色。
     *
     * @param id 空间ID
     * @return 空间视图VO
     */
    public SpaceVO detail(Long id) {
        // 校验用户为空间成员，同时返回用户在该空间的角色
        SpaceRole role = permissionService.requireMember(id);
        return getSpace(id).toVO(role);
    }

    /**
     * 更新空间基础信息
     * 权限：需要空间OWNER所有者权限；DTO中null字段不会更新数据库。
     *
     * @param id 待更新空间ID
     * @param dto 空间更新请求DTO
     * @return 更新完成后的空间VO
     */
    public SpaceVO update(Long id, SpaceUpdateDTO dto) {
        permissionService.requireRole(id, SpaceRole.OWNER);
        // 获取空间实体，不存在抛404
        SpaceEntity space = getSpace(id);
        // 将dto非空字段应用到实体
        dto.applyTo(space);
        spaceMapper.updateById(space);
        return space.toVO(SpaceRole.OWNER);
    }

    /**
     * 删除空间
     * 权限：需要空间OWNER所有者权限；
     * 事务：逻辑删除空间主记录，同时删除该空间下全部成员关联记录，异常整体回滚。
     * 注意：当前仅处理space与member，**不会级联删除文档、版本数据**，业务上需要考虑后续数据清理/软删除。
     *
     * @param id 空间ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        permissionService.requireRole(id, SpaceRole.OWNER);
        // 校验空间必须存在
        getSpace(id);
        // 逻辑删除空间主表
        spaceMapper.deleteById(id);
        // 删除该空间下全部成员记录
        memberMapper.delete(new LambdaQueryWrapper<MemberEntity>()
                .eq(MemberEntity::getSpaceId, id));
    }

    /**
     * 根据ID查询空间实体，不存在抛出404业务异常
     *
     * @param id 空间ID
     * @return 空间数据库实体
     */
    private SpaceEntity getSpace(Long id) {
        SpaceEntity space = spaceMapper.selectById(id);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "空间不存在");
        }
        return space;
    }
}
