package com.agentdoc.auth.mapper;

import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.pojo.vo.DepartmentMemberCountVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户 Mapper。
 */
public interface UserMapper extends BaseMapper<UserEntity> {

    /** 锁定有效用户行，序列化同一账号的状态和密码变更。 */
    @Select("SELECT * FROM `user` WHERE id = #{userId} AND deleted = 0 FOR UPDATE")
    UserEntity lockById(@Param("userId") Long userId);

    /** 原子推进人类认证版本及单调递增的OIDC拒绝边界。 */
    @Update("UPDATE `user` SET auth_version = auth_version + 1, "
            + "oidc_auth_not_before = GREATEST(oidc_auth_not_before, #{notBefore}) "
            + "WHERE id = #{userId} AND deleted = 0")
    int advanceAuthenticationVersion(@Param("userId") Long userId, @Param("notBefore") long notBefore);

    /** 在保护角色锁内读取当前启用超管，不能沿用调用方先前的一致性快照。 */
    @Select("SELECT u.id FROM `user` u JOIN user_platform_role b ON b.user_id = u.id "
            + "JOIN platform_role r ON r.id = b.role_id WHERE u.deleted = 0 AND u.status = 1 "
            + "AND r.deleted = 0 AND r.role_key = #{roleKey} ORDER BY u.id FOR UPDATE")
    List<Long> lockEnabledRoleUsers(@Param("roleKey") String roleKey);

    /**
     * 按部门统计未删除用户数量。
     * @return 部门直属成员数量
     */
    @Select("SELECT department_id AS departmentId, COUNT(*) AS memberCount "
            + "FROM `user` WHERE deleted = 0 AND department_id IS NOT NULL GROUP BY department_id")
    List<DepartmentMemberCountVO> countByDepartment();
}
