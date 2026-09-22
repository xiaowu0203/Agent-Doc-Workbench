package com.agentdoc.document.mapper;

import com.agentdoc.document.pojo.entity.DocumentDirectoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档目录 Mapper。
 */
public interface DocumentDirectoryMapper extends BaseMapper<DocumentDirectoryEntity> {
    List<DocumentDirectoryEntity> selectNormalHierarchy(@Param("spaceId") Long spaceId);
}
