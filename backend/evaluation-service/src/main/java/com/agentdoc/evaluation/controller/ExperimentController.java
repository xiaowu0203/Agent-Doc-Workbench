package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.evaluation.pojo.dto.ExperimentCreateDTO;
import com.agentdoc.evaluation.pojo.dto.ExperimentDecisionDTO;
import com.agentdoc.evaluation.pojo.dto.ExperimentReportRecalculateDTO;
import com.agentdoc.evaluation.pojo.dto.ExperimentStartDTO;
import com.agentdoc.evaluation.pojo.vo.ExperimentPreflightVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentReportRevisionVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentReportVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentVO;
import com.agentdoc.evaluation.pojo.vo.ExperimentVariantVO;
import com.agentdoc.evaluation.service.ExperimentService;
import com.agentdoc.evaluation.service.ExperimentReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Experiment", description = "离线 baseline/candidate 实验")
@RestController
@RequestMapping("/api/evaluation/experiments")
@RequireLogin
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService service;
    private final ExperimentReportService reportService;

    @Operation(summary = "创建不可变 Experiment 与 Prompt Variant")
    @PostMapping
    public Result<ExperimentVO> create(@Valid @RequestBody ExperimentCreateDTO request) {
        return Result.ok(service.create(request));
    }

    @Operation(summary = "查询 Experiment")
    @GetMapping("/{id}")
    public Result<ExperimentVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @Operation(summary = "查询 Experiment Variant")
    @GetMapping("/{id}/variants")
    public Result<List<ExperimentVariantVO>> variants(@PathVariable Long id) {
        return Result.ok(service.variants(id));
    }

    @Operation(summary = "执行动态 Replay 与预算预检")
    @GetMapping("/{id}/preflight")
    public Result<ExperimentPreflightVO> preflight(@PathVariable Long id) {
        return Result.ok(service.preflight(id));
    }

    @Operation(summary = "启动或恢复 Experiment")
    @PostMapping("/{id}/start")
    public Result<ExperimentVO> start(@PathVariable Long id,
                                      @Valid @RequestBody ExperimentStartDTO request) {
        return Result.ok(service.start(id, request));
    }

    @Operation(summary = "幂等取消 Experiment")
    @PutMapping("/{id}/cancel")
    public Result<ExperimentVO> cancel(@PathVariable Long id) {
        return Result.ok(service.cancel(id));
    }

    @Operation(summary = "查询 Experiment 的不可变报告版本")
    @GetMapping("/{id}/reports")
    public Result<List<ExperimentReportRevisionVO>> reports(@PathVariable Long id) {
        return Result.ok(reportService.list(id));
    }

    @Operation(summary = "查询指定报告版本")
    @GetMapping("/{id}/reports/{revision}")
    public Result<ExperimentReportVO> report(@PathVariable Long id, @PathVariable Integer revision) {
        return Result.ok(reportService.detail(id, revision));
    }

    @Operation(summary = "显式重算报告；输入不变时复用已有版本")
    @PostMapping("/{id}/reports/recalculate")
    public Result<ExperimentReportVO> recalculate(@PathVariable Long id,
            @Valid @RequestBody ExperimentReportRecalculateDTO request) {
        return Result.ok(reportService.recalculate(id, request));
    }

    @Operation(summary = "针对指定报告版本作人工结论")
    @PostMapping("/{id}/decision")
    public Result<Void> decision(@PathVariable Long id, @Valid @RequestBody ExperimentDecisionDTO request) {
        reportService.decide(id, request);
        return Result.ok(null);
    }
}
