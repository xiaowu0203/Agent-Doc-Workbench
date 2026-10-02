package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.evaluation.pojo.param.EvaluationRunSearchParam;
import com.agentdoc.evaluation.pojo.param.ExperimentSearchParam;
import com.agentdoc.evaluation.pojo.vo.DatasetCaseBindingVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationCaseAttemptHistoryVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationRunSummaryVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentSummaryVO;
import com.agentdoc.evaluation.pojo.vo.TestCaseEvaluatorBindingVO;
import com.agentdoc.evaluation.service.EvaluationWorkbenchQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Evaluation Workbench", description = "评估工作台轻量查询与历史下钻")
@RestController
@RequestMapping("/api/evaluation")
@RequireLogin
@RequiredArgsConstructor
public class EvaluationWorkbenchController {

    private final EvaluationWorkbenchQueryService queryService;

    @Operation(summary = "分页搜索 EvaluationRun")
    @PostMapping("/runs/search")
    public Result<PageVO<EvaluationRunSummaryVO>> searchRuns(
            @Valid @RequestBody EvaluationRunSearchParam param) {
        return Result.ok(queryService.searchRuns(param));
    }

    @Operation(summary = "分页搜索 Experiment")
    @PostMapping("/experiments/search")
    public Result<PageVO<ExperimentSummaryVO>> searchExperiments(
            @Valid @RequestBody ExperimentSearchParam param) {
        return Result.ok(queryService.searchExperiments(param));
    }

    @Operation(summary = "查询 DatasetVersion 的测试用例绑定")
    @GetMapping("/dataset-versions/{id}/cases")
    public Result<List<DatasetCaseBindingVO>> datasetCases(@PathVariable Long id) {
        return Result.ok(queryService.datasetCases(id));
    }

    @Operation(summary = "查询 TestCaseVersion 的评估器绑定")
    @GetMapping("/test-case-versions/{id}/evaluators")
    public Result<List<TestCaseEvaluatorBindingVO>> testCaseEvaluators(@PathVariable Long id) {
        return Result.ok(queryService.testCaseEvaluators(id));
    }

    @Operation(summary = "分页查询 CaseRun 的 Attempt 与 Result 历史")
    @PostMapping("/case-runs/{id}/attempts/search")
    public Result<PageVO<EvaluationCaseAttemptHistoryVO>> caseAttempts(
            @PathVariable Long id, @Valid @RequestBody PageParam param) {
        return Result.ok(queryService.caseAttempts(id, param));
    }
}
