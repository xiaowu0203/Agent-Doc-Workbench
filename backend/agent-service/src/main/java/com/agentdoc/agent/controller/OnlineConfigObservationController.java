package com.agentdoc.agent.controller;
import com.agentdoc.agent.service.OnlineConfigObservationService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 保护扫描专用当前依赖证明。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/internal/online-config-observation")
public class OnlineConfigObservationController {
    private final OnlineConfigObservationService service;
    @GetMapping("/{experimentId}/dependency")
    public Result<String> dependency(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token) { return Result.ok(service.dependency(experimentId, token)); }
}
