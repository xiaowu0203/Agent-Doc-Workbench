package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.evaluation.pojo.dto.EvaluationRunCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationRunResumeDTO;
import com.agentdoc.evaluation.pojo.vo.EvaluationRunVO;
import com.agentdoc.evaluation.service.EvaluationRunService;
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
import org.springframework.web.bind.annotation.PutMapping;

@Tag(name = "Evaluation Run", description = "评估运行创建与查询")
@RestController
@RequestMapping("/api/evaluation/runs")
@RequireLogin
@RequiredArgsConstructor
public class EvaluationRunController {

    private final EvaluationRunService service;

    @Operation(summary = "创建并派发 EvaluationRun")
    @PostMapping
    public Result<EvaluationRunVO> create(@Valid @RequestBody EvaluationRunCreateDTO request) {
        return Result.ok(service.create(request));
    }

    @Operation(summary = "查询 EvaluationRun")
    @GetMapping("/{id}")
    public Result<EvaluationRunVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @Operation(summary = "恢复 PAUSED EvaluationRun")
    @PutMapping("/{id}/resume")
    public Result<EvaluationRunVO> resume(@PathVariable Long id,
                                          @Valid @RequestBody EvaluationRunResumeDTO request) {
        return Result.ok(service.resume(id, request));
    }

    @Operation(summary = "取消 EvaluationRun 及其活跃 Replay")
    @PutMapping("/{id}/cancel")
    public Result<EvaluationRunVO> cancel(@PathVariable Long id) {
        return Result.ok(service.cancel(id));
    }
}
