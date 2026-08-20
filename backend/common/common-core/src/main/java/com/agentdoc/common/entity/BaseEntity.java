package com.agentdoc.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体基类：各业务实体继承，统一公共字段。
 * <ul>
 *     <li>{@code id}：雪花 ID（MyBatis-Plus ASSIGN_ID 策略）</li>
 *     <li>{@code createdAt}：创建时间（DDL 默认 CURRENT_TIMESTAMP 兜底）</li>
 * </ul>
 * 常规业务表（含 created_at / updated_at / deleted 三列）建议继承 {@link BaseLogicDeleteEntity}；
 * 流水/日志类表（无 deleted / updated_at，如 token_usage、audit_log）直接继承本类。
 * 依赖说明：本类使用 MyBatis-Plus 注解，common-core 中以 optional 声明 mybatis-plus-annotation，
 * 使用方（auth/document/task）需自行引入 mybatis-plus；gateway 不加载本类，不受影响。
 */
@Data
public abstract class BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private LocalDateTime createdAt;
}
