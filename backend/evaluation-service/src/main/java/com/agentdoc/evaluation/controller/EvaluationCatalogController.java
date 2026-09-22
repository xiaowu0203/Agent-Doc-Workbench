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
import io.swagger.v3.oas.annotations.Parameter;
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

    @Operation(summary = "数据集分页搜索", description = "按名称、标签、空间等条件检索数据集列表，返回分页结果")
    @PostMapping("/datasets/search")
    public Result<PageVO<EvaluationDatasetVO>> searchDatasets(
            @Valid @RequestBody EvaluationResourceSearchParam param) {
        return Result.ok(catalogService.searchDatasets(param));
    }

    @Operation(summary = "数据集版本分页搜索", description = "检索数据集版本记录，支持按状态、版本号、所属数据集过滤")
    @PostMapping("/dataset-versions/search")
    public Result<PageVO<DatasetVersionVO>> searchDatasetVersions(
            @Valid @RequestBody EvaluationVersionSearchParam param) {
        return Result.ok(catalogService.searchDatasetVersions(param));
    }

    @Operation(summary = "测试用例分页搜索", description = "检索测试用例主记录，支持名称、标签、空间过滤")
    @PostMapping("/test-cases/search")
    public Result<PageVO<EvaluationTestCaseVO>> searchTestCases(
            @Valid @RequestBody EvaluationResourceSearchParam param) {
        return Result.ok(catalogService.searchTestCases(param));
    }

    @Operation(summary = "测试用例版本分页搜索", description = "检索测试用例版本，可按发布状态、来源任务过滤")
    @PostMapping("/test-case-versions/search")
    public Result<PageVO<TestCaseVersionVO>> searchTestCaseVersions(
            @Valid @RequestBody EvaluationVersionSearchParam param) {
        return Result.ok(catalogService.searchTestCaseVersions(param));
    }

    @Operation(summary = "评估器分页搜索", description = "检索评估器主记录，支持名称、类型、空间过滤")
    @PostMapping("/evaluators/search")
    public Result<PageVO<EvaluatorVO>> searchEvaluators(
            @Valid @RequestBody EvaluationResourceSearchParam param) {
        return Result.ok(catalogService.searchEvaluators(param));
    }

    @Operation(summary = "评估器版本分页搜索", description = "检索评估器版本记录，支持契约版本、发布状态过滤")
    @PostMapping("/evaluator-versions/search")
    public Result<PageVO<EvaluatorVersionVO>> searchEvaluatorVersions(
            @Valid @RequestBody EvaluationVersionSearchParam param) {
        return Result.ok(catalogService.searchEvaluatorVersions(param));
    }

    @Operation(summary = "获取数据集详情", description = "根据主键ID查询数据集基础信息")
    @GetMapping("/datasets/{id}")
    public Result<EvaluationDatasetVO> getDataset(
            @Parameter(description = "数据集ID") @PathVariable Long id) {
        return Result.ok(catalogService.getDataset(id));
    }

    @Operation(summary = "归档数据集", description = "软归档数据集，归档后不可新建版本，已有版本仍可用于评估运行")
    @PutMapping("/datasets/{id}/archive")
    public Result<EvaluationDatasetVO> archiveDataset(
            @Parameter(description = "数据集ID") @PathVariable Long id) {
        return Result.ok(catalogService.archiveDataset(id));
    }

    @Operation(summary = "获取数据集版本详情", description = "按ID查询单条数据集版本快照，包含绑定的用例引用")
    @GetMapping("/dataset-versions/{id}")
    public Result<DatasetVersionVO> getDatasetVersion(
            @Parameter(description = "数据集版本ID") @PathVariable Long id) {
        return Result.ok(catalogService.getDatasetVersion(id));
    }

    @Operation(summary = "归档数据集版本", description = "软归档指定数据集版本，已归档版本不能再被选为评估运行基线")
    @PutMapping("/dataset-versions/{id}/archive")
    public Result<DatasetVersionVO> archiveDatasetVersion(
            @Parameter(description = "数据集版本ID") @PathVariable Long id) {
        return Result.ok(catalogService.archiveDatasetVersion(id));
    }

    @Operation(summary = "获取测试用例详情", description = "查询测试用例主记录基础信息")
    @GetMapping("/test-cases/{id}")
    public Result<EvaluationTestCaseVO> getTestCase(
            @Parameter(description = "测试用例ID") @PathVariable Long id) {
        return Result.ok(catalogService.getTestCase(id));
    }

    @Operation(summary = "归档测试用例", description = "软归档测试用例，归档后禁止新建版本，存量版本可继续回放评估")
    @PutMapping("/test-cases/{id}/archive")
    public Result<EvaluationTestCaseVO> archiveTestCase(
            @Parameter(description = "测试用例ID") @PathVariable Long id) {
        return Result.ok(catalogService.archiveTestCase(id));
    }

    @Operation(summary = "获取测试用例版本详情", description = "查询单条测试用例冻结版本，包含基线快照哈希与评估器绑定")
    @GetMapping("/test-case-versions/{id}")
    public Result<TestCaseVersionVO> getTestCaseVersion(
            @Parameter(description = "测试用例版本ID") @PathVariable Long id) {
        return Result.ok(catalogService.getTestCaseVersion(id));
    }

    @Operation(summary = "归档测试用例版本", description = "软归档指定用例版本，该版本不再允许新建评估运行")
    @PutMapping("/test-case-versions/{id}/archive")
    public Result<TestCaseVersionVO> archiveTestCaseVersion(
            @Parameter(description = "测试用例版本ID") @PathVariable Long id) {
        return Result.ok(catalogService.archiveTestCaseVersion(id));
    }

    @Operation(summary = "获取评估器详情", description = "查询评估器主记录信息")
    @GetMapping("/evaluators/{id}")
    public Result<EvaluatorVO> getEvaluator(
            @Parameter(description = "评估器ID") @PathVariable Long id) {
        return Result.ok(catalogService.getEvaluator(id));
    }

    @Operation(summary = "归档评估器", description = "软归档评估器，归档后不能创建新版本，存量版本可继续被已有用例引用")
    @PutMapping("/evaluators/{id}/archive")
    public Result<EvaluatorVO> archiveEvaluator(
            @Parameter(description = "评估器ID") @PathVariable Long id) {
        return Result.ok(catalogService.archiveEvaluator(id));
    }

    @Operation(summary = "获取评估器版本详情", description = "查询评估器版本，包含指标契约定义信息")
    @GetMapping("/evaluator-versions/{id}")
    public Result<EvaluatorVersionVO> getEvaluatorVersion(
            @Parameter(description = "评估器版本ID") @PathVariable Long id) {
        return Result.ok(catalogService.getEvaluatorVersion(id));
    }

    @Operation(summary = "归档评估器版本", description = "软归档评估器版本，该版本不再允许被新测试用例绑定")
    @PutMapping("/evaluator-versions/{id}/archive")
    public Result<EvaluatorVersionVO> archiveEvaluatorVersion(
            @Parameter(description = "评估器版本ID") @PathVariable Long id) {
        return Result.ok(catalogService.archiveEvaluatorVersion(id));
    }

    @Operation(summary = "创建 Dataset", description = "新建数据集主记录，初始无版本，需要再创建草稿版本")
    @PostMapping("/datasets")
    public Result<EvaluationDatasetVO> createDataset(@Valid @RequestBody EvaluationDatasetCreateDTO dto) {
        return Result.ok(catalogService.createDataset(dto));
    }

    @Operation(summary = "创建 Dataset DRAFT 版本", description = "为数据集新建草稿版本，草稿状态不可用于评估运行，需发布后生效")
    @PostMapping("/dataset-versions")
    public Result<DatasetVersionVO> createDatasetVersion(@Valid @RequestBody DatasetVersionCreateDTO dto) {
        return Result.ok(catalogService.createDatasetVersion(dto));
    }

    @Operation(summary = "替换 DRAFT Dataset 的用例绑定", description = "仅草稿版本可操作；全量替换当前数据集版本绑定的测试用例集合")
    @PutMapping("/dataset-versions/{id}/cases")
    public Result<DatasetVersionVO> replaceDatasetCases(
            @Parameter(description = "数据集草稿版本ID") @PathVariable Long id,
            @Valid @RequestBody DatasetCaseBindingsDTO dto) {
        return Result.ok(catalogService.replaceDatasetCases(id, dto));
    }

    @Operation(summary = "发布 Dataset 版本", description = "将草稿版本转为正式LIVE版本，冻结用例绑定，可用于启动评估运行")
    @PutMapping("/dataset-versions/{id}/publish")
    public Result<DatasetVersionVO> publishDatasetVersion(
            @Parameter(description = "数据集草稿版本ID") @PathVariable Long id) {
        return Result.ok(catalogService.publishDatasetVersion(id));
    }

    @Operation(summary = "创建 TestCase", description = "新建测试用例主记录，仅主体，需继续创建草稿版本")
    @PostMapping("/test-cases")
    public Result<EvaluationTestCaseVO> createTestCase(@Valid @RequestBody EvaluationTestCaseCreateDTO dto) {
        return Result.ok(catalogService.createTestCase(dto));
    }

    @Operation(summary = "从 LIVE Task 创建 TestCase DRAFT 版本", description = "基于一条已完成的真实任务快照，生成可编辑的测试用例草稿版本，包含基线哈希，支持回放")
    @PostMapping("/test-case-versions")
    public Result<TestCaseVersionVO> createTestCaseVersion(@Valid @RequestBody TestCaseVersionCreateDTO dto) {
        return Result.ok(catalogService.createTestCaseVersion(dto));
    }

    @Operation(summary = "替换 DRAFT TestCase 的评估器绑定", description = "仅草稿版本允许；全量替换本用例版本绑定的评估器列表")
    @PutMapping("/test-case-versions/{id}/evaluators")
    public Result<TestCaseVersionVO> replaceTestCaseEvaluators(
            @Parameter(description = "测试用例草稿版本ID") @PathVariable Long id,
            @Valid @RequestBody TestCaseEvaluatorBindingsDTO dto) {
        return Result.ok(catalogService.replaceTestCaseEvaluators(id, dto));
    }

    @Operation(summary = "发布 TestCase 版本", description = "草稿转正、冻结基线快照与评估器绑定，成为可被数据集引用的正式用例版本")
    @PutMapping("/test-case-versions/{id}/publish")
    public Result<TestCaseVersionVO> publishTestCaseVersion(
            @Parameter(description = "测试用例草稿版本ID") @PathVariable Long id) {
        return Result.ok(catalogService.publishTestCaseVersion(id));
    }

    @Operation(summary = "创建 Evaluator", description = "新建评估器主记录，后续创建版本存放指标契约逻辑")
    @PostMapping("/evaluators")
    public Result<EvaluatorVO> createEvaluator(@Valid @RequestBody EvaluatorCreateDTO dto) {
        return Result.ok(catalogService.createEvaluator(dto));
    }

    @Operation(summary = "创建 Evaluator DRAFT 版本", description = "新建评估器草稿版本，定义指标契约、计算公式；草稿不可绑定到正式用例")
    @PostMapping("/evaluator-versions")
    public Result<EvaluatorVersionVO> createEvaluatorVersion(@Valid @RequestBody EvaluatorVersionCreateDTO dto) {
        return Result.ok(catalogService.createEvaluatorVersion(dto));
    }

    @Operation(summary = "发布 Evaluator 版本", description = "评估器草稿版本转正，指标契约冻结，可被正式测试用例版本绑定使用")
    @PutMapping("/evaluator-versions/{id}/publish")
    public Result<EvaluatorVersionVO> publishEvaluatorVersion(
            @Parameter(description = "评估器草稿版本ID") @PathVariable Long id) {
        return Result.ok(catalogService.publishEvaluatorVersion(id));
    }
}
