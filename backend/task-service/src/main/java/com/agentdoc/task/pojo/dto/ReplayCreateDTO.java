package com.agentdoc.task.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建 Replay 的幂等请求。 */
public record ReplayCreateDTO(
        @NotBlank(message = "派生请求幂等键不能为空")
        @Size(max = 191, message = "派生请求幂等键最长 191 字符")
        String derivationRequestKey) {
}
