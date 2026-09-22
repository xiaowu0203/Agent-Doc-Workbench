package com.agentdoc.evaluation.pojo.vo;
import com.agentdoc.evaluation.pojo.entity.EvaluatorEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "评估器基础信息")
public record EvaluatorVO(
        @Schema(description = "评估器ID")
        Long id,

        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "评估器名称")
        String name,

        @Schema(description = "评估器业务唯一Key")
        String evaluatorKey,

        @Schema(description = "评估器描述")
        String description,

        @Schema(description = "是否已归档")
        Boolean archived,

        @Schema(description = "创建人ID")
        Long createdBy
) {
    /**
     * 数据库实体转换为视图对象
     * @param value 评估器实体
     * @return 对外VO
     */
    public static EvaluatorVO from(EvaluatorEntity value) {
        return new EvaluatorVO(value.getId(), value.getSpaceId(), value.getName(), value.getEvaluatorKey(),
                value.getDescription(), value.getArchived(), value.getCreatedBy());
    }
}