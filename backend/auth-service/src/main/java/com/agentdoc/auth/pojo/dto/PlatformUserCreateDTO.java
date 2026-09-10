package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_EMAIL_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_JOB_TITLE_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_NICKNAME_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_PASSWORD_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_USERNAME_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.MIN_PASSWORD_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.MIN_USERNAME_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.USERNAME_PATTERN;

/**
 * 平台管理员创建用户请求。
 */
@Schema(description = "平台管理员创建用户请求")
public record PlatformUserCreateDTO(
        @NotBlank(message = "用户名不能为空")
        @Size(min = MIN_USERNAME_LENGTH, max = MAX_USERNAME_LENGTH, message = "用户名长度为 3-32")
        @Pattern(regexp = USERNAME_PATTERN, message = "用户名仅允许字母、数字、下划线")
        @Schema(description = "用户名")
        String username,

        @NotBlank(message = "初始密码不能为空")
        @Size(min = MIN_PASSWORD_LENGTH, max = MAX_PASSWORD_LENGTH, message = "密码长度为 6-64")
        @Schema(description = "初始密码")
        String password,

        @Size(max = MAX_NICKNAME_LENGTH, message = "昵称过长")
        @Schema(description = "昵称；为空时使用用户名")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = MAX_EMAIL_LENGTH, message = "邮箱过长")
        @Schema(description = "邮箱")
        String email,

        @Schema(description = "所属部门 ID")
        Long departmentId,

        @Size(max = MAX_JOB_TITLE_LENGTH, message = "职位过长")
        @Schema(description = "职位")
        String jobTitle,

        @Min(value = 0, message = "用户状态无效")
        @Max(value = 1, message = "用户状态无效")
        @Schema(description = "状态：0 禁用 / 1 启用；为空时默认启用")
        Integer status,

        @Schema(description = "是否同时授予平台超级管理员；为空时默认否")
        Boolean superAdmin
) {
}
