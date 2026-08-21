package com.agentdoc.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类：各业务实体继承，统一公共字段。
 * 常规业务表（含 created_at / updated_at / deleted 三列）建议继承 {@link BaseLogicDeleteEntity}；
 * 流水/日志类表（无 deleted / updated_at，如 token_usage、audit_log）直接继承本类。
 */
@Data
public abstract class BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
