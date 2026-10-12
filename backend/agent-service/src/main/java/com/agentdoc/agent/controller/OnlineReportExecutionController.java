package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.OnlineReportExecutionFactsService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.OnlineReportFactsQueryDTO;
import com.agentdoc.common.feign.vo.OnlineReportExecutionFactVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/agent/internal/online-report")
public class OnlineReportExecutionController {
    private final OnlineReportExecutionFactsService service;
    @PostMapping("/facts")
    public Result<List<OnlineReportExecutionFactVO>> facts(@RequestBody OnlineReportFactsQueryDTO request) { return Result.ok(service.facts(request)); }
}
