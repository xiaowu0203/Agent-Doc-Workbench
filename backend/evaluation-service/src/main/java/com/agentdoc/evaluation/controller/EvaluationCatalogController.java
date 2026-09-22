package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.evaluation.pojo.dto.DatasetCaseBindingsDTO;
import com.agentdoc.evaluation.pojo.dto.DatasetVersionCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationDatasetCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluatorCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluatorVersionCreateDTO;
import com.agentdoc.evaluation.pojo.dto.EvaluationTestCaseCreateDTO;
import com.agentdoc.evaluation.pojo.dto.TestCaseEvaluatorBindingsDTO;
import com.agentdoc.evaluation.pojo.dto.TestCaseVersionCreateDTO;
import com.agentdoc.evaluation.pojo.param.EvaluationResourceSearchParam;
import com.agentdoc.evaluation.pojo.param.EvaluationVersionSearchParam;
import com.agentdoc.evaluation.pojo.vo.DatasetVersionVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationDatasetVO;
import com.agentdoc.evaluation.pojo.vo.EvaluatorVO;
import com.agentdoc.evaluation.pojo.vo.EvaluatorVersionVO;
import com.agentdoc.evaluation.pojo.vo.EvaluationTestCaseVO;
import com.agentdoc.evaluation.pojo.vo.TestCaseVersionVO;
import com.agentdoc.evaluation.service.EvaluationCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "评估资源版本管理")
@RequireLogin
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
public class EvaluationCatalogController {
    private final EvaluationCatalogService catalogService;

    @PostMapping("/datasets/search")
    public Result<PageVO<EvaluationDatasetVO>> searchDatasets(
            @Valid @RequestBody EvaluationResourceSearchParam param) {
        return Result.ok(catalogService.searchDatasets(param));
    }
    @PostMapping("/dataset-versions/search")
    public Result<PageVO<DatasetVersionVO>> searchDatasetVersions(
            @Valid @RequestBody EvaluationVersionSearchParam param) {
        return Result.ok(catalogService.searchDatasetVersions(param));
    }
    @PostMapping("/test-cases/search")
    public Result<PageVO<EvaluationTestCaseVO>> searchTestCases(
            @Valid @RequestBody EvaluationResourceSearchParam param) {
        return Result.ok(catalogService.searchTestCases(param));
    }
    @PostMapping("/test-case-versions/search")
    public Result<PageVO<TestCaseVersionVO>> searchTestCaseVersions(
            @Valid @RequestBody EvaluationVersionSearchParam param) {
        return Result.ok(catalogService.searchTestCaseVersions(param));
    }
    @PostMapping("/evaluators/search")
    public Result<PageVO<EvaluatorVO>> searchEvaluators(
            @Valid @RequestBody EvaluationResourceSearchParam param) {
        return Result.ok(catalogService.searchEvaluators(param));
    }
    @PostMapping("/evaluator-versions/search")
    public Result<PageVO<EvaluatorVersionVO>> searchEvaluatorVersions(
            @Valid @RequestBody EvaluationVersionSearchParam param) {
        return Result.ok(catalogService.searchEvaluatorVersions(param));
    }

    @GetMapping("/datasets/{id}")
    public Result<EvaluationDatasetVO> getDataset(@PathVariable Long id) {
        return Result.ok(catalogService.getDataset(id));
    }
    @PutMapping("/datasets/{id}/archive")
    public Result<EvaluationDatasetVO> archiveDataset(@PathVariable Long id) {
        return Result.ok(catalogService.archiveDataset(id));
    }
    @GetMapping("/dataset-versions/{id}")
    public Result<DatasetVersionVO> getDatasetVersion(@PathVariable Long id) {
        return Result.ok(catalogService.getDatasetVersion(id));
    }
    @PutMapping("/dataset-versions/{id}/archive")
    public Result<DatasetVersionVO> archiveDatasetVersion(@PathVariable Long id) {
        return Result.ok(catalogService.archiveDatasetVersion(id));
    }
    @GetMapping("/test-cases/{id}")
    public Result<EvaluationTestCaseVO> getTestCase(@PathVariable Long id) {
        return Result.ok(catalogService.getTestCase(id));
    }
    @PutMapping("/test-cases/{id}/archive")
    public Result<EvaluationTestCaseVO> archiveTestCase(@PathVariable Long id) {
        return Result.ok(catalogService.archiveTestCase(id));
    }
    @GetMapping("/test-case-versions/{id}")
    public Result<TestCaseVersionVO> getTestCaseVersion(@PathVariable Long id) {
        return Result.ok(catalogService.getTestCaseVersion(id));
    }
    @PutMapping("/test-case-versions/{id}/archive")
    public Result<TestCaseVersionVO> archiveTestCaseVersion(@PathVariable Long id) {
        return Result.ok(catalogService.archiveTestCaseVersion(id));
    }
    @GetMapping("/evaluators/{id}")
    public Result<EvaluatorVO> getEvaluator(@PathVariable Long id) {
        return Result.ok(catalogService.getEvaluator(id));
    }
    @PutMapping("/evaluators/{id}/archive")
    public Result<EvaluatorVO> archiveEvaluator(@PathVariable Long id) {
        return Result.ok(catalogService.archiveEvaluator(id));
    }
    @GetMapping("/evaluator-versions/{id}")
    public Result<EvaluatorVersionVO> getEvaluatorVersion(@PathVariable Long id) {
        return Result.ok(catalogService.getEvaluatorVersion(id));
    }
    @PutMapping("/evaluator-versions/{id}/archive")
    public Result<EvaluatorVersionVO> archiveEvaluatorVersion(@PathVariable Long id) {
        return Result.ok(catalogService.archiveEvaluatorVersion(id));
    }

    @Operation(summary = "创建 Dataset")
    @PostMapping("/datasets")
    public Result<EvaluationDatasetVO> createDataset(@Valid @RequestBody EvaluationDatasetCreateDTO dto) {
        return Result.ok(catalogService.createDataset(dto));
    }
    @Operation(summary = "创建 Dataset DRAFT 版本")
    @PostMapping("/dataset-versions")
    public Result<DatasetVersionVO> createDatasetVersion(@Valid @RequestBody DatasetVersionCreateDTO dto) {
        return Result.ok(catalogService.createDatasetVersion(dto));
    }
    @Operation(summary = "替换 DRAFT Dataset 的用例绑定")
    @PutMapping("/dataset-versions/{id}/cases")
    public Result<DatasetVersionVO> replaceDatasetCases(@PathVariable Long id,
            @Valid @RequestBody DatasetCaseBindingsDTO dto) {
        return Result.ok(catalogService.replaceDatasetCases(id, dto));
    }
    @Operation(summary = "发布 Dataset 版本")
    @PutMapping("/dataset-versions/{id}/publish")
    public Result<DatasetVersionVO> publishDatasetVersion(@PathVariable Long id) {
        return Result.ok(catalogService.publishDatasetVersion(id));
    }
    @Operation(summary = "创建 TestCase")
    @PostMapping("/test-cases")
    public Result<EvaluationTestCaseVO> createTestCase(@Valid @RequestBody EvaluationTestCaseCreateDTO dto) {
        return Result.ok(catalogService.createTestCase(dto));
    }
    @Operation(summary = "从 LIVE Task 创建 TestCase DRAFT 版本")
    @PostMapping("/test-case-versions")
    public Result<TestCaseVersionVO> createTestCaseVersion(@Valid @RequestBody TestCaseVersionCreateDTO dto) {
        return Result.ok(catalogService.createTestCaseVersion(dto));
    }
    @Operation(summary = "替换 DRAFT TestCase 的评估器绑定")
    @PutMapping("/test-case-versions/{id}/evaluators")
    public Result<TestCaseVersionVO> replaceTestCaseEvaluators(@PathVariable Long id,
            @Valid @RequestBody TestCaseEvaluatorBindingsDTO dto) {
        return Result.ok(catalogService.replaceTestCaseEvaluators(id, dto));
    }
    @Operation(summary = "发布 TestCase 版本")
    @PutMapping("/test-case-versions/{id}/publish")
    public Result<TestCaseVersionVO> publishTestCaseVersion(@PathVariable Long id) {
        return Result.ok(catalogService.publishTestCaseVersion(id));
    }
    @Operation(summary = "创建 Evaluator")
    @PostMapping("/evaluators")
    public Result<EvaluatorVO> createEvaluator(@Valid @RequestBody EvaluatorCreateDTO dto) {
        return Result.ok(catalogService.createEvaluator(dto));
    }
    @Operation(summary = "创建 Evaluator DRAFT 版本")
    @PostMapping("/evaluator-versions")
    public Result<EvaluatorVersionVO> createEvaluatorVersion(@Valid @RequestBody EvaluatorVersionCreateDTO dto) {
        return Result.ok(catalogService.createEvaluatorVersion(dto));
    }
    @Operation(summary = "发布 Evaluator 版本")
    @PutMapping("/evaluator-versions/{id}/publish")
    public Result<EvaluatorVersionVO> publishEvaluatorVersion(@PathVariable Long id) {
        return Result.ok(catalogService.publishEvaluatorVersion(id));
    }
}
