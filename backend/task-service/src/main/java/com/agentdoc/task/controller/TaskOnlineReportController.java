package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.OnlineReportFactsQueryDTO;
import com.agentdoc.common.feign.vo.OnlineReportTaskFactVO;
import com.agentdoc.task.service.TaskOnlineReportFactsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/task/internal/online-report")
public class TaskOnlineReportController {
    private final TaskOnlineReportFactsService service;
    @PostMapping("/facts")
    public Result<List<OnlineReportTaskFactVO>> facts(@RequestBody OnlineReportFactsQueryDTO request) { return Result.ok(service.facts(request)); }
}
