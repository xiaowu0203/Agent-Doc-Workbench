package com.agentdoc.auth.mapper;

import com.agentdoc.auth.pojo.entity.UserEntity;
import com.agentdoc.auth.pojo.vo.DepartmentMemberCountVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper。
 */
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 按部门统计未删除用户数量。
     * @return 部门直属成员数量
     */
    @Select("SELECT department_id AS departmentId, COUNT(*) AS memberCount "
            + "FROM `user` WHERE deleted = 0 AND department_id IS NOT NULL GROUP BY department_id")
    List<DepartmentMemberCountVO> countByDepartment();
}
