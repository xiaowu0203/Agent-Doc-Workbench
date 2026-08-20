package com.agentdoc.common.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 逻辑删除扩展基类：在 {@link BaseEntity}（id / createdAt）基础上增加
 * updatedAt 与逻辑删除标记 deleted。
 * 适用于包含 created_at / updated_at / deleted 三列的常规业务表；
 * 流水/日志类表（无 deleted / updated_at）直接继承 {@link BaseEntity}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLogicDeleteEntity extends BaseEntity {

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
