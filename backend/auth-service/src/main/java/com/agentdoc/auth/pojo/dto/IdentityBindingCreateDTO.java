package com.agentdoc.auth.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 管理员核验后的外部身份；不得按邮箱自动推断。 */
@Schema(description = "外部身份预绑定请求")
public record IdentityBindingCreateDTO(
        @NotBlank @Schema(description = "精确可信issuer") String issuer,
        @NotBlank @Schema(description = "外部身份subject") String subject
) { }
