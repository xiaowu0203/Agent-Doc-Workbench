package com.agentdoc.evaluation.pojo.entity;
import com.agentdoc.common.pojo.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("evaluation_dataset_case")
@Schema(description = "数据集版本用例绑定")
public class EvaluationDatasetCaseEntity extends BaseEntity {
    @Schema(description = "数据集版本 ID") private Long datasetVersionId;
    @Schema(description = "测试用例版本 ID") private Long testCaseVersionId;
    @Schema(description = "顺序") private Integer sortOrder;
    @Schema(description = "是否启用") private Boolean enabled;
}
