package com.agentdoc.task.pojo.vo;

import com.agentdoc.task.pojo.entity.ExecutionArtifactEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 隔离执行不可变候选产物 VO。
 * <p>
 * 承载一次工具执行产出的持久化工件，属于不可变数据；一旦生成不允许更新，仅新增。
 * 用于回放、评估、证据溯源：payloadJson 为原始内容，payloadSha256 做完整性校验，schemaVersion 用于版本兼容解析。
 * </p>
 */
@Schema(description = "隔离执行不可变候选产物")
public record ExecutionArtifactVO(
        @Schema(description = "工件主键ID")
        Long id,
        @Schema(description = "归属任务ID")
        Long taskId,
        @Schema(description = "执行实例ID，一次task可包含多段execution")
        Long executionId,
        @Schema(description = "原始源任务ID，来自上游业务侧")
        Long sourceTaskId,
        @Schema(description = "同一次execution内工件序号，用于排序")
        Integer sequenceNo,
        @Schema(description = "源工具调用ID，关联单次tool_call")
        Long sourceToolCallId,
        @Schema(description = "工件类型，如 DOCUMENT_SNAPSHOT / LLM_RESPONSE / TOOL_OUTPUT 等")
        String artifactType,
        @Schema(description = "payloadJson 的结构版本号，用于向前兼容解析")
        Integer schemaVersion,
        @Schema(description = "工件原始JSON载荷，大文本")
        String payloadJson,
        @Schema(description = "payloadJson 的 SHA‑256 摘要，用于防篡改校验")
        String payloadSha256,
        @Schema(description = "创建时间，工件落库时刻")
        LocalDateTime createdAt
) {
    /**
     * 数据库实体 → VO 转换。
     * @param entity 持久化实体
     * @return 对外视图对象
     */
    public static ExecutionArtifactVO from(ExecutionArtifactEntity entity) {
        return new ExecutionArtifactVO(
                entity.getId(),
                entity.getTaskId(),
                entity.getExecutionId(),
                entity.getSourceTaskId(),
                entity.getSequenceNo(),
                entity.getSourceToolCallId(),
                entity.getArtifactType(),
                entity.getSchemaVersion(),
                entity.getPayloadJson(),
                entity.getPayloadSha256(),
                entity.getCreatedAt()
        );
    }
}