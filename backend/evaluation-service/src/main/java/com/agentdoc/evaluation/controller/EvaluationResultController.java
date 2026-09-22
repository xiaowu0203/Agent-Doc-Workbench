package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.evaluation.pojo.vo.EvaluationResultDetailVO;
import com.agentdoc.evaluation.service.EvaluationResultQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "评估结果查询")
@RequireLogin
@RestController
@RequestMapping("/api/evaluation/results")
@RequiredArgsConstructor
public class EvaluationResultController {
    private final EvaluationResultQueryService resultQueryService;

    @Operation(summary = "查询 Result、标准化 Metric 与 Evidence")
    @GetMapping("/{id}")
    public Result<EvaluationResultDetailVO> detail(@PathVariable Long id) {
        return Result.ok(resultQueryService.detail(id));
    }
}
