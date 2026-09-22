package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.evaluation.pojo.dto.EvaluationRetryDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationRunResumeDTO;
import com.agentdoc.evaluation.pojo.vo.EvaluationRunVO;
import com.agentdoc.evaluation.service.EvaluationRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Evaluation Retry", description = "Replay 与 Evaluator 两级显式重试")
@RestController
@RequestMapping("/api/evaluation")
@RequireLogin
@RequiredArgsConstructor
public class EvaluationRetryController {
    private final EvaluationRunService service;

    @Operation(summary = "创建新的 CaseAttempt 并重试 Replay")
    @PostMapping("/case-runs/{id}/retry-replay")
    public Result<EvaluationRunVO> retryReplay(@PathVariable Long id,
                                               @Valid @RequestBody EvaluationRunResumeDTO request) {
        return Result.ok(service.retryReplay(id, request));
    }

    @Operation(summary = "在同一 CaseAttempt 追加 Evaluator Result attempt")
    @PostMapping("/case-attempts/{id}/retry-evaluation")
    public Result<EvaluationRunVO> retryEvaluation(@PathVariable Long id,
                                                   @Valid @RequestBody EvaluationRetryDTO request) {
        return Result.ok(service.retryEvaluation(id, request));
    }
}
