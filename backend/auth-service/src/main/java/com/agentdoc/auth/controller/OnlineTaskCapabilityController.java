package com.agentdoc.auth.controller;

import com.agentdoc.auth.service.OnlineTaskCapabilityIssuanceService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/internal/online-waits")
public class OnlineTaskCapabilityController {
    private final OnlineTaskCapabilityIssuanceService service;
    @PostMapping("/{taskId}/renew")
    public Result<String> renew(@PathVariable String taskId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String wait) { return Result.ok(service.renew(taskId, wait)); }
    @RequireLogin
    @PostMapping("/{taskId}/human")
    public Result<String> initial(@PathVariable String taskId) { return Result.ok(service.initial(taskId)); }
    @PostMapping("/{taskId}/exchange")
    public Result<String> exchange(@PathVariable String taskId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String wait) { return Result.ok(service.exchange(taskId, wait)); }
}
