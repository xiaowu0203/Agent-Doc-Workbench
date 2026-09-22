package com.agentdoc.evaluation.pojo.entity;
import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("test_case_evaluator")
@Schema(description = "测试用例评估器绑定")
public class TestCaseEvaluatorEntity extends BaseEntity {
    @Schema(description = "测试用例版本 ID") private Long testCaseVersionId;
    @Schema(description = "评估器版本 ID") private Long evaluatorVersionId;
    @Schema(description = "用例期望 JSON") private String expectedJson;
    @Schema(description = "顺序") private Integer sortOrder;
}
