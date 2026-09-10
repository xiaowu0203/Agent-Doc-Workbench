package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_EMAIL_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_JOB_TITLE_LENGTH;
import static com.agentdoc.auth.constant.PlatformManagementConstant.MAX_NICKNAME_LENGTH;

/**
 * 平台管理员修改用户资料请求。
 */
@Schema(description = "平台管理员修改用户资料请求")
public record PlatformUserUpdateDTO(
        @NotBlank(message = "昵称不能为空")
        @Size(max = MAX_NICKNAME_LENGTH, message = "昵称过长")
        @Schema(description = "昵称")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = MAX_EMAIL_LENGTH, message = "邮箱过长")
        @Schema(description = "邮箱")
        String email,

        @Schema(description = "所属部门 ID；为空表示未分配")
        Long departmentId,

        @Size(max = MAX_JOB_TITLE_LENGTH, message = "职位过长")
        @Schema(description = "职位")
        String jobTitle
) {
}
