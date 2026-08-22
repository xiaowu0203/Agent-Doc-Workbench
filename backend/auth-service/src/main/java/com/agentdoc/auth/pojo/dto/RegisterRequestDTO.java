package com.agentdoc.auth.pojo.dto;

import com.agentdoc.auth.enums.UserStatus;
import com.agentdoc.auth.pojo.entity.UserEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
@Schema(description = "注册请求")
public record RegisterRequestDTO(
        @Schema(description = "用户名，仅允许字母、数字、下划线")
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 32, message = "用户名长度为 3-32")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母、数字、下划线")
        String username,

        @Schema(description = "明文密码")
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度为 6-64")
        String password,

        @Schema(description = "昵称，为空时默认使用用户名")
        @Size(max = 50, message = "昵称过长")
        String nickname,

        @Schema(description = "邮箱")
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱过长")
        String email
) {

    /**
     * 请求转实体：字段搬运统一收敛到实体侧。
     * @param passwordHash 已编码的密码哈希（由 Service 调用 PasswordEncoder 生成）
     * @return 新注册用户实体，默认启用状态
     */
    public UserEntity toEntity(String passwordHash) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        user.setEmail(email);
        user.setStatus(UserStatus.ENABLED.getCode());
        return user;
    }
}
