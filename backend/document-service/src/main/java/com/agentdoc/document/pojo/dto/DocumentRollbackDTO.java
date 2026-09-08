package com.agentdoc.document.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 文档版本回滚请求。 */
@Schema(description = "文档版本回滚请求")
public record DocumentRollbackDTO(
        @Schema(description = "回滚目标版本号") @NotNull Long targetVersion,
        @Schema(description = "发起回滚时的当前版本号，用于并发校验") @NotNull Long baseVersion) {
}

