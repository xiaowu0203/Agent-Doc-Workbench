package com.agentdoc.evaluation.pojo.vo;
import com.agentdoc.evaluation.pojo.entity.EvaluationDatasetEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "数据集信息")
public record EvaluationDatasetVO(
        @Schema(description = "数据集ID")
        Long id,

        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "数据集名称")
        String name,

        @Schema(description = "数据集描述")
        String description,

        @Schema(description = "是否已归档")
        Boolean archived,

        @Schema(description = "创建人ID")
        Long createdBy,

        @Schema(description = "创建时间")
        LocalDateTime createdAt
) {
    /**
     * 数据库实体转换为视图对象
     * @param value 数据集实体
     * @return 对外VO
     */
    public static EvaluationDatasetVO from(EvaluationDatasetEntity value) {
        return new EvaluationDatasetVO(value.getId(), value.getSpaceId(), value.getName(), value.getDescription(),
                value.getArchived(), value.getCreatedBy(), value.getCreatedAt());
    }
}