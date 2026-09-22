package com.agentdoc.document.mapper;

import com.agentdoc.document.pojo.entity.MemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 空间成员 Mapper。
 */
public interface MemberMapper extends BaseMapper<MemberEntity> {
    List<Long> selectPermittedSpaceIds(@Param("userId") Long userId,
                                       @Param("spaceIds") Collection<Long> spaceIds,
                                       @Param("permissionCode") String permissionCode);
}
