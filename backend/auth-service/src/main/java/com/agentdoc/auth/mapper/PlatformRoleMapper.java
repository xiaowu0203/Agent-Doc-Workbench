package com.agentdoc.auth.mapper;

import com.agentdoc.auth.pojo.entity.PlatformRoleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/**
 * 平台角色 Mapper。
 */
public interface PlatformRoleMapper extends BaseMapper<PlatformRoleEntity> {
    /** 管理写入统一先锁保护角色，再锁目标用户，避免跨用户最后超管竞态。 */
    @Select("SELECT * FROM platform_role WHERE role_key = #{roleKey} AND deleted = 0 FOR UPDATE")
    PlatformRoleEntity lockByKey(@Param("roleKey") String roleKey);
}
