package com.agentdoc.task.controller;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.constant.HeaderConstants;
import com.agentdoc.common.feign.dto.DocumentVersionRollbackAuditDTO;
import com.agentdoc.common.feign.dto.DocumentVersionSourceQueryDTO;
import com.agentdoc.common.feign.dto.SpaceRoleAuditDTO;
import com.agentdoc.common.feign.vo.DocumentVersionSourceVO;
import com.agentdoc.task.service.DocumentVersionAuditService;
import com.agentdoc.task.service.DocumentVersionSourceQueryService;
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

    public InternalTaskController(TaskService taskService,
                                  DocumentVersionSourceQueryService versionSourceQueryService,
                                  DocumentVersionAuditService versionAuditService,
                                  AuditLogService auditLogService) {
        this.taskService = taskService;
        this.versionSourceQueryService = versionSourceQueryService;
        this.versionAuditService = versionAuditService;
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "校验X‑TASK‑CAPABILITY任务能力令牌是否对指定taskId业务有效")
    @GetMapping("/{taskId}/capability")
    public Result<Void> checkCapability(
            @PathVariable Long taskId,
            @RequestHeader(value = HeaderConstants.X_TASK_CAPABILITY, required = false) String token) {
        taskService.checkCapability(taskId, token);
        return Result.ok();
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
}
