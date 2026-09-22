package com.agentdoc.evaluation.pojo.vo;

import com.agentdoc.evaluation.pojo.entity.EvaluationTestCaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "测试用例基础信息")
public record EvaluationTestCaseVO(
        @Schema(description = "测试用例ID")
        Long id,

        @Schema(description = "所属空间ID")
        Long spaceId,

        @Schema(description = "测试用例名称")
        String name,

        @Schema(description = "测试用例描述")
        String description,

        @Schema(description = "是否已归档")
        Boolean archived,

        @Schema(description = "创建人ID")
        Long createdBy
) {
    /**
     * 数据库实体转换为视图对象
     * @param value 测试用例实体
     * @return 对外VO
     */
    public static EvaluationTestCaseVO from(EvaluationTestCaseEntity value) {
        return new EvaluationTestCaseVO(value.getId(), value.getSpaceId(), value.getName(), value.getDescription(),
                value.getArchived(), value.getCreatedBy());
    }
}