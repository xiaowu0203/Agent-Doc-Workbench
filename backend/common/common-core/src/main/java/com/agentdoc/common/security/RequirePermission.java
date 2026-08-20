package com.agentdoc.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口需要指定权限。Phase 1 仅校验登录态，value 的细粒度权限（空间成员关系/角色）
 * 在 Phase 2 结合空间成员表实现。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限标识，如 "space:write" */
    String value() default "";
}
