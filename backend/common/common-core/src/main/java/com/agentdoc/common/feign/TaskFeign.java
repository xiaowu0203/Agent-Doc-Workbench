package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.feign.dto.DocumentVersionRollbackAuditDTO;
import com.agentdoc.common.feign.dto.DocumentVersionSourceQueryDTO;
import com.agentdoc.common.feign.dto.ExecutionArtifactAppendDTO;
import com.agentdoc.common.feign.dto.EvaluationTaskBatchQueryDTO;
import com.agentdoc.common.feign.dto.EvaluationWorkerCapabilityRenewDTO;
import com.agentdoc.common.feign.dto.ReplayBatchCreateDTO;
import com.agentdoc.common.feign.dto.SpaceRoleAuditDTO;
import com.agentdoc.common.feign.dto.WorkbenchSearchQueryDTO;
import com.agentdoc.common.feign.vo.DocumentVersionSourceVO;
import com.agentdoc.common.feign.vo.ChangeRequestFeedbackSnapshotVO;
import com.agentdoc.common.feign.vo.ExecutionArtifactAppendVO;
import com.agentdoc.common.feign.vo.EvaluationEvidenceBundleVO;
import com.agentdoc.common.feign.vo.EvaluationDocumentChangeEvidenceVO;
import com.agentdoc.common.feign.vo.EvaluationTaskCancelVO;
import com.agentdoc.common.feign.vo.EvaluationTaskStatusVO;
import com.agentdoc.common.feign.vo.EvaluationWorkerCapabilityVO;
import com.agentdoc.common.feign.vo.ReplaySourceVO;
import com.agentdoc.common.feign.vo.ReplayBatchCreateVO;
import com.agentdoc.common.feign.vo.WorkbenchSearchGroupVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;

import java.util.List;

/**
 * Task 服务内部能力校验契约。
 */
@FeignClient(name = "task-service", url = "${agent-doc.feign.gateway-url:http://localhost:9090}")
public interface TaskFeign {

    /**
     * 检查任务是否具备执行能力。
     */
    @GetMapping("/api/task/internal/tasks/{taskId}/capability")
    Result<Void> checkTaskCapability(@PathVariable Long taskId);

    /** 追加不可变的隔离执行候选产物。 */
    @PostMapping("/api/task/internal/tasks/{taskId}/execution-artifacts")
    Result<ExecutionArtifactAppendVO> appendExecutionArtifact(
            @PathVariable Long taskId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(HeaderConstants.X_TASK_CAPABILITY) String capability,
            @RequestBody ExecutionArtifactAppendDTO request);

    /** 查询评估用例可冻结的 Replay 来源。 */
    @GetMapping("/api/task/internal/tasks/{taskId}/replay-source")
    Result<ReplaySourceVO> getReplaySource(@PathVariable Long taskId, @RequestParam Long spaceId);

    /** 在当前用户权限上下文中批量创建 Replay 并取得窄权限 WorkerCapability。 */
    @PostMapping("/api/task/tasks/replays/batch")
    Result<ReplayBatchCreateVO> createReplayBatch(@RequestBody ReplayBatchCreateDTO request);

    /** 在当前用户权限上下文中，为既有 Replay Task 重新签发窄权限 WorkerCapability。 */
    @PostMapping("/api/task/tasks/evaluation-worker-capability")
    Result<EvaluationWorkerCapabilityVO> renewEvaluationWorkerCapability(
            @RequestBody EvaluationWorkerCapabilityRenewDTO request);

    /** 使用 WorkerCapability 批量查询本 Run Replay 状态。 */
    @PostMapping("/api/task/internal/tasks/evaluation/status/query")
    Result<List<EvaluationTaskStatusVO>> queryEvaluationTaskStatuses(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) String capability,
            @RequestBody EvaluationTaskBatchQueryDTO request);

    default Result<List<EvaluationTaskStatusVO>> queryEvaluationTaskStatuses(
            String capability, EvaluationTaskBatchQueryDTO request) {
        return queryEvaluationTaskStatuses(bearer(capability), capability, request);
    }

    /** 使用 WorkerCapability 批量读取确定性评估事实。 */
    @PostMapping("/api/task/internal/tasks/evaluation/evidence/query")
    Result<List<EvaluationEvidenceBundleVO>> queryEvaluationEvidence(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) String capability,
            @RequestBody EvaluationTaskBatchQueryDTO request);

    default Result<List<EvaluationEvidenceBundleVO>> queryEvaluationEvidence(
            String capability, EvaluationTaskBatchQueryDTO request) {
        return queryEvaluationEvidence(bearer(capability), capability, request);
    }

    /** 使用 WorkerCapability 读取文档变更专项校验事实，不返回正文或 payload。 */
    @PostMapping("/api/task/internal/tasks/evaluation/document-changes/query")
    Result<List<EvaluationDocumentChangeEvidenceVO>> queryEvaluationDocumentChanges(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) String capability,
            @RequestBody EvaluationTaskBatchQueryDTO request);

    default Result<List<EvaluationDocumentChangeEvidenceVO>> queryEvaluationDocumentChanges(
            String capability, EvaluationTaskBatchQueryDTO request) {
        return queryEvaluationDocumentChanges(bearer(capability), capability, request);
    }

    /** 使用 WorkerCapability 批量终止本 Run 绑定的 Replay。 */
    @PostMapping("/api/task/internal/tasks/evaluation/cancel")
    Result<List<EvaluationTaskCancelVO>> cancelEvaluationTasks(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(HeaderConstants.X_EVALUATION_WORKER_CAPABILITY) String capability,
            @RequestBody EvaluationTaskBatchQueryDTO request);

    default Result<List<EvaluationTaskCancelVO>> cancelEvaluationTasks(
            String capability, EvaluationTaskBatchQueryDTO request) {
        return cancelEvaluationTasks(bearer(capability), capability, request);
    }

    private static String bearer(String capability) {
        return "Bearer " + capability;
    }

    /** 在当前用户权限下读取最终审批事实的脱敏快照。 */
    @GetMapping("/api/task/internal/tasks/change-requests/{id}/evaluation-feedback")
    Result<ChangeRequestFeedbackSnapshotVO> getChangeRequestFeedbackSnapshot(@PathVariable Long id);

    /** 批量查询文档版本关联的任务与审批展示信息。 */
    @PostMapping("/api/task/internal/tasks/version-sources/query")
    Result<List<DocumentVersionSourceVO>> queryDocumentVersionSources(
            @RequestBody DocumentVersionSourceQueryDTO request);

    /** 记录文档版本回滚审计。 */
    @PostMapping("/api/task/internal/tasks/document-version-rollback-audit")
    Result<Void> recordDocumentVersionRollback(@RequestBody DocumentVersionRollbackAuditDTO request);

    /** 记录空间角色与权限变更审计。 */
    @PostMapping("/api/task/internal/tasks/space-role-audit")
    Result<Void> recordSpaceRoleAudit(@RequestBody SpaceRoleAuditDTO request);

    /** 查询工作台任务搜索结果。 */
    @PostMapping("/api/task/internal/tasks/workbench-search")
    Result<WorkbenchSearchGroupVO> searchWorkbench(@RequestBody WorkbenchSearchQueryDTO request);
}
