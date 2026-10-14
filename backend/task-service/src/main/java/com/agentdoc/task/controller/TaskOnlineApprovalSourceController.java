package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.OnlineApprovalSourceVO;
import com.agentdoc.task.service.TaskOnlineApprovalSourceService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireLogin
@RequiredArgsConstructor
public class TaskOnlineApprovalSourceController {
    private final TaskOnlineApprovalSourceService sources;

    @Operation(summary = "读取已授权线上任务的原始审批来源")
    @GetMapping("/api/task/change-requests/{id}/online-approval-source")
    public Result<OnlineApprovalSourceVO> get(@PathVariable Long id) { return Result.ok(sources.get(id)); }
}
