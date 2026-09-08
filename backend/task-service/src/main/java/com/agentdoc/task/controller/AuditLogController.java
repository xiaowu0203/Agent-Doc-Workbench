package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.task.pojo.param.AuditLogSearchParam;
import com.agentdoc.task.pojo.vo.AuditLogVO;
import com.agentdoc.task.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "审计日志", description = "空间级追加型审计日志查询")
@RestController
@RequestMapping("/api/task/audit-logs")
@RequireLogin
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "分页查询空间审计日志")
    @PostMapping("/query")
    public Result<PageVO<AuditLogVO>> query(@Valid @RequestBody AuditLogSearchParam param) {
        return Result.ok(auditLogService.search(param));
    }
}

