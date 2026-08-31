package com.agentdoc.document.mapper;

import com.agentdoc.document.pojo.entity.SpaceRolePermissionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

/**
 * 空间角色权限绑定 Mapper。
 */
public interface SpaceRolePermissionMapper extends BaseMapper<SpaceRolePermissionEntity> {

    /**
     * 批量插入角色权限绑定，避免按权限逐条执行 INSERT。
     *
     * @param entities 角色权限绑定集合
     * @return 插入记录数
     */
    int insertBatch(@Param("entities") Collection<SpaceRolePermissionEntity> entities);
}
