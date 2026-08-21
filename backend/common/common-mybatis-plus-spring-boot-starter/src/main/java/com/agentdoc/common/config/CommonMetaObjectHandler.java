package com.agentdoc.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充处理器（MetaObjectHandler），由 common-mybatis-plus-spring-boot-starter 自动装配。
 * <p>
 * 配合基类字段注解生效（strictInsertFill / strictUpdateFill 仅填充声明了对应 fill 的字段）：
 * <ul>
 *     <li>{@link com.agentdoc.common.entity.BaseEntity#createdAt}：{@code @TableField(fill = FieldFill.INSERT)} → 插入时填充 {@code now}</li>
 *     <li>{@link com.agentdoc.common.entity.BaseLogicDeleteEntity#updatedAt}：{@code @TableField(fill = FieldFill.INSERT_UPDATE)} → 插入/更新时填充 {@code now}</li>
 * </ul>
 * 未声明 fill 的字段（如流水表无 updatedAt）会被 strict 方法自动跳过；{@code deleted} 字段无 fill，
 * 仍由 DDL 默认值 {@code 0} 兜底，不在此处处理。
 */
public class CommonMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
