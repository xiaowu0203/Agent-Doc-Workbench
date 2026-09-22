package com.agentdoc.evaluation.pojo.vo;
import com.agentdoc.evaluation.pojo.entity.EvaluatorVersionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "评估器版本信息")
public record EvaluatorVersionVO(
        @Schema(description = "评估器版本ID")
        Long id,

        @Schema(description = "所属评估器ID")
        Long evaluatorId,

        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "版本号")
        Integer versionNo,

        @Schema(description = "版本状态 DRAFT/LIVE")
        String status,

        @Schema(description = "评估器业务唯一Key")
        String evaluatorKey,

        @Schema(description = "配置Schema版本")
        Integer configSchemaVersion,

        @Schema(description = "评估器契约配置JSON")
        String configJson,

        @Schema(description = "结果输出Schema版本")
        Integer resultSchemaVersion,

        @Schema(description = "评估器实现版本")
        String implementationVersion,

        @Schema(description = "配置内容哈希")
        String contentHash,

        @Schema(description = "发布时间，草稿版本为空")
        LocalDateTime publishedAt,

        @Schema(description = "创建人ID")
        Long createdBy
) {
    /**
     * 数据库实体转换为视图对象
     * @param value 评估器版本实体
     * @return 对外VO
     */
    public static EvaluatorVersionVO from(EvaluatorVersionEntity value) {
        return new EvaluatorVersionVO(value.getId(), value.getEvaluatorId(), value.getSpaceId(), value.getVersionNo(),
                value.getStatus(), value.getEvaluatorKey(), value.getConfigSchemaVersion(), value.getConfigJson(),
                value.getResultSchemaVersion(), value.getImplementationVersion(), value.getContentHash(),
                value.getPublishedAt(), value.getCreatedBy());
    }
}