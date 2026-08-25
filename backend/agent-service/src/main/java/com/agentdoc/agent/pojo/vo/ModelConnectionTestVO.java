package com.agentdoc.agent.pojo.vo;

import com.agentdoc.agent.enums.ModelErrorType;
import io.swagger.v3.oas.annotations.media.Schema;

/** 模型配置连通性测试结果。 */
@Schema(description = "模型配置连通性测试结果")
public record ModelConnectionTestVO(
        @Schema(description = "是否连接成功") boolean connected,
        @Schema(description = "模型供应商") String provider,
        @Schema(description = "统一错误类型；连接成功时为空") ModelErrorType errorType,
        @Schema(description = "厂商返回的 HTTP 状态码") Integer statusCode,
        @Schema(description = "是否建议重试") boolean retryable,
        @Schema(description = "测试结果说明") String message) {
}
