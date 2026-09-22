package com.agentdoc.task.controller;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.feign.dto.DocumentVersionRollbackAuditDTO;
import com.agentdoc.common.feign.dto.DocumentVersionSourceQueryDTO;
import com.agentdoc.common.feign.dto.ExecutionArtifactAppendDTO;
import com.agentdoc.common.feign.dto.EvaluationTaskBatchQueryDTO;
import com.agentdoc.common.feign.dto.SpaceRoleAuditDTO;
import com.agentdoc.common.feign.dto.WorkbenchSearchQueryDTO;
import com.agentdoc.common.feign.vo.DocumentVersionSourceVO;
import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.common.feign.vo.ExecutionArtifactAppendVO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangeEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationTaskCancelVO;
import com.agentdoc.common.feign.vo.EvaluationTaskStatusVO;
import com.agentdoc.common.feign.vo.ReplaySourceVO;
import com.agentdoc.common.feign.vo.WorkbenchSearchGroupVO;
import com.agentdoc.task.service.DocumentVersionAuditService;
import com.agentdoc.task.service.ChangeRequestFeedbackProjectionService;
import com.agentdoc.task.service.DocumentVersionSourceQueryService;
import com.agentdoc.task.service.ExecutionArtifactService;
import com.agentdoc.task.service.EvaluationTaskProjectionService;
import com.agentdoc.task.service.AuditLogService;
import com.agentdoc.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Agent 任务内部接口", description = "令牌-任务校验是否匹配")
@RestController
@RequestMapping("/api/task/internal/tasks")
public class InternalTaskController {

    private final TaskService taskService;
    private final DocumentVersionSourceQueryService versionSourceQueryService;
    private final DocumentVersionAuditService versionAuditService;
    private final AuditLogService auditLogService;
    private final ExecutionArtifactService executionArtifactService;
    private final EvaluationTaskProjectionService evaluationTaskProjectionService;
    private final ChangeRequestFeedbackProjectionService changeRequestFeedbackProjectionService;

    public InternalTaskController(TaskService taskService,
                                  DocumentVersionSourceQueryService versionSourceQueryService,
                                  DocumentVersionAuditService versionAuditService,
                                  AuditLogService auditLogService,
                                  ExecutionArtifactService executionArtifactService,
                                  EvaluationTaskProjectionService evaluationTaskProjectionService,
                                  ChangeRequestFeedbackProjectionService changeRequestFeedbackProjectionService) {
        this.taskService = taskService;
        this.versionSourceQueryService = versionSourceQueryService;
        this.versionAuditService = versionAuditService;
        this.auditLogService = auditLogService;
        this.executionArtifactService = executionArtifactService;
        this.evaluationTaskProjectionService = evaluationTaskProjectionService;
        this.changeRequestFeedbackProjectionService = changeRequestFeedbackProjectionService;
    }

    @Operation(summary = "校验X‑TASK‑CAPABILITY任务能力令牌是否对指定taskId业务有效")
    @GetMapping("/{taskId}/capability")
    public Result<Void> checkCapability(
            @PathVariable Long taskId,
            @RequestHeader(value = HeaderConstants.X_TASK_CAPABILITY, required = false) String token) {
        taskService.checkCapability(taskId, token);
        return Result.ok();
    }

    @Operation(summary = "追加隔离执行候选产物")
    @PostMapping("/{taskId}/execution-artifacts")
    public Result<ExecutionArtifactAppendVO> appendExecutionArtifact(
            @PathVariable Long taskId,
            @RequestHeader(value = HeaderConstants.X_TASK_CAPABILITY, required = false) String token,
            @RequestBody ExecutionArtifactAppendDTO request) {
        return Result.ok(executionArtifactService.append(taskId, token, request));
    }

    @Operation(summary = "查询评估用例可冻结的 Replay 来源")
    @RequireLogin
    @GetMapping("/{taskId}/replay-source")
    public Result<ReplaySourceVO> getReplaySource(@PathVariable Long taskId, @RequestParam Long spaceId) {
        return Result.ok(taskService.replaySource(taskId, spaceId));
    }

    @Operation(summary = "批量查询文档版本来源信息")
    @RequireLogin
    @PostMapping("/version-sources/query")
    public Result<List<DocumentVersionSourceVO>> queryVersionSources(
            @RequestBody DocumentVersionSourceQueryDTO request) {
        return Result.ok(versionSourceQueryService.query(request));
    }

    @Operation(summary = "记录文档版本回滚审计")
    @RequireLogin
    @PostMapping("/document-version-rollback-audit")
    public Result<Void> recordVersionRollback(@RequestBody DocumentVersionRollbackAuditDTO request) {
        versionAuditService.recordRollback(request);
        return Result.ok();
    }

    @Operation(summary = "记录空间角色与权限变更审计")
    @RequireLogin
    @PostMapping("/space-role-audit")
    public Result<Void> recordSpaceRoleAudit(@RequestBody SpaceRoleAuditDTO request) {
        auditLogService.recordSpaceRoleAudit(request);
        return Result.ok();
    }

    @Operation(summary = "查询工作台任务搜索结果")
    @RequireLogin
    @PostMapping("/workbench-search")
    public Result<WorkbenchSearchGroupVO> searchWorkbench(@RequestBody WorkbenchSearchQueryDTO request) {
        return Result.ok(taskService.searchWorkbench(request));
    }

    @Operation(summary = "批量查询 Evaluation Replay 状态")
    @PostMapping("/evaluation/status/query")
    public Result<List<EvaluationTaskStatusVO>> evaluationStatuses(
            @RequestHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) String capability,
            @RequestBody EvaluationTaskBatchQueryDTO request) {
        return Result.ok(evaluationTaskProjectionService.statuses(capability, request));
    }

    @Operation(summary = "批量查询 Evaluation 权威事实")
    @PostMapping("/evaluation/evidence/query")
    public Result<List<EvaluationEvidenceBundleVO>> evaluationEvidence(
            @RequestHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) String capability,
            @RequestBody EvaluationTaskBatchQueryDTO request) {
        return Result.ok(evaluationTaskProjectionService.evidence(capability, request));
    }

    @Operation(summary = "批量校验 Evaluation 候选文档变更")
    @PostMapping("/evaluation/document-changes/query")
    public Result<List<EvaluationDocumentChangeEvidenceVO>> evaluationDocumentChanges(
            @RequestHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) String capability,
            @RequestBody EvaluationTaskBatchQueryDTO request) {
        return Result.ok(evaluationTaskProjectionService.documentChanges(capability, request));
    }

    @Operation(summary = "批量取消 Evaluation Replay")
    @PostMapping("/evaluation/cancel")
    public Result<List<EvaluationTaskCancelVO>> cancelEvaluationTasks(
            @RequestHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) String capability,
            @RequestBody EvaluationTaskBatchQueryDTO request) {
        return Result.ok(evaluationTaskProjectionService.cancel(capability, request));
    }

    @Operation(summary = "读取 ChangeRequest 最终审批事实快照")
    @RequireLogin
    @GetMapping("/change-requests/{id}/evaluation-feedback")
    public Result<ChangeRequestFeedbackSnapshotVO> changeRequestFeedback(@PathVariable Long id) {
        return Result.ok(changeRequestFeedbackProjectionService.get(id));
    }
}
