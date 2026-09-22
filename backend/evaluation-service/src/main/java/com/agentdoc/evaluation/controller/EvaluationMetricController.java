package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.MetricComparisonQueryDTO;
import com.agentdoc.common.feign.vo.MetricComparisonInputVO;
import com.agentdoc.common.feign.vo.StandardMetricVO;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.evaluation.pojo.param.MetricSearchParam;
import com.agentdoc.evaluation.service.EvaluationMetricQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Evaluation Metric", description = "标准 Metric 查询与对比输入")
@RestController
@RequestMapping("/api/evaluation/metrics")
@RequireLogin
@RequiredArgsConstructor
public class EvaluationMetricController {
    private final EvaluationMetricQueryService service;

    @Operation(summary = "分页查询标准 Metric 的有效或历史视图")
    @PostMapping("/search")
    public Result<PageVO<StandardMetricVO>> search(@RequestBody MetricSearchParam request) {
        return Result.ok(service.search(request));
    }

    @Operation(summary = "生成 Phase 4 可直接消费的标准 Metric 对比输入")
    @PostMapping("/compare-input")
    public Result<MetricComparisonInputVO> comparisonInput(@RequestBody MetricComparisonQueryDTO request) {
        return Result.ok(service.comparisonInput(request));
    }
}
