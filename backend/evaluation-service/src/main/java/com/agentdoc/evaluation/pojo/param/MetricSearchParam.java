package com.agentdoc.evaluation.pojo.param;

import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.evaluation.enums.EvaluationMetricSelection;
import com.agentdoc.evaluation.enums.EvaluationMetricSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

import static com.agentdoc.evaluation.constant.EvaluationConstant.MAX_METRIC_QUERY_IDS;
import static com.agentdoc.evaluation.constant.EvaluationConstant.MAX_METRIC_QUERY_KEYS;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "标准 Metric 分页查询条件")
public class MetricSearchParam extends PageParam {

    @Schema(description = "所属空间 ID")
    private Long spaceId;

    @Schema(description = "EvaluationRun ID 列表，批量过滤评估运行")
    private List<Long> runIds;

    @Schema(description = "TestCaseVersion ID 列表，批量过滤测试用例版本")
    private List<Long> testCaseVersionIds;

    @Schema(description = "Metric key 列表，按指标标识过滤")
    private List<String> metricKeys;

    @Schema(description = "Metric 来源列表：系统自动评估 / 人工反馈等")
    private List<EvaluationMetricSource> sources;

    @Schema(description = "查询视图，默认 EFFECTIVE", defaultValue = "EFFECTIVE")
    private EvaluationMetricSelection selection = EvaluationMetricSelection.EFFECTIVE;

    @Override
    public void validate() {
        super.validate();
        if (spaceId == null || spaceId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "spaceId 必须为正整数");
        }
        requireSize(runIds, MAX_METRIC_QUERY_IDS, "runIds");
        requireSize(testCaseVersionIds, MAX_METRIC_QUERY_IDS, "testCaseVersionIds");
        requireSize(metricKeys, MAX_METRIC_QUERY_KEYS, "metricKeys");
        if (selection == null) {
            selection = EvaluationMetricSelection.EFFECTIVE;
        }
    }

    /**
     * 校验列表参数长度，限制批量查询上限，避免查询过载
     * @param values 待校验集合
     * @param maxSize 最大允许条数
     * @param field 字段名，用于异常提示
     */
    private static void requireSize(List<?> values, int maxSize, String field) {
        if (values != null && values.size() > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, field + " 最多允许 " + maxSize + " 项");
        }
    }
}