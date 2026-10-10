package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.utils.OnlineAssignmentRequestParser;
import com.agentdoc.common.feign.vo.OnlineRouteVO;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineSlotPermitVO;
import com.agentdoc.evaluation.service.OnlineAssignmentAccessService;
import com.agentdoc.evaluation.service.OnlineRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/evaluation/internal/online-assignments")
public class OnlineAssignmentController {
    private final OnlineRoutingService routing;
    private final OnlineAssignmentAccessService access;
    @RequireLogin
    @GetMapping("/{taskId}/human-binding")
    public Result<OnlineTaskBindingDTO> humanBinding(@PathVariable String taskId) { return Result.ok(access.humanBinding(taskId)); }
    @GetMapping("/{taskId}/binding")
    public Result<OnlineTaskBindingDTO> binding(@PathVariable String taskId, @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String wait) {
        return Result.ok(access.waitBinding(taskId, wait));
    }
    @GetMapping("/{taskId}/waiting")
    public Result<OnlineTaskBindingDTO> waiting(@PathVariable String taskId, @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String wait) {
        return Result.ok(access.waiting(taskId, wait));
    }
    @PostMapping("/{taskId}/claim")
    public Result<OnlineSlotPermitVO> claim(@PathVariable String taskId, @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String wait) {
        return Result.ok(access.claim(taskId, wait));
    }
    @GetMapping("/{taskId}/permit")
    public Result<OnlineSlotPermitVO> permit(@PathVariable String taskId, @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String wait) {
        return Result.ok(access.permit(taskId, wait));
    }
    @GetMapping("/{taskId}/execution-permit")
    public Result<OnlineSlotPermitVO> executionPermit(@PathVariable String taskId) { return Result.ok(access.executionPermit(taskId)); }
    @PostMapping("/{taskId}/begin")
    public Result<OnlineSlotPermitVO> begin(@PathVariable String taskId) { return Result.ok(access.begin(taskId)); }
    @RequireLogin
    @PostMapping("/route")
    public Result<OnlineRouteVO> route(@RequestBody String json) { return Result.ok(routing.route(OnlineAssignmentRequestParser.parse(json))); }
    @RequireLogin
    @PostMapping("/check-scope")
    public Result<Boolean> scope(@RequestBody String json) { return Result.ok(routing.checkScope(OnlineAssignmentRequestParser.parse(json))); }
}
