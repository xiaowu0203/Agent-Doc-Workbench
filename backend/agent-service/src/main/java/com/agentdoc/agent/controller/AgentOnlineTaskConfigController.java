package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentOnlineConfigService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.OnlineAssignmentRequestParser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/agent/internal/online-configs")
public class AgentOnlineTaskConfigController {
    private final AgentOnlineConfigService service;
    @PostMapping("/{experimentId}/task-dependency")
    public Result<String> dependency(@PathVariable String experimentId, @RequestBody String json) {
        return Result.ok(service.taskDependency(OnlineProtocolUtils.id(experimentId), OnlineAssignmentRequestParser.parse(json)));
    }
}
