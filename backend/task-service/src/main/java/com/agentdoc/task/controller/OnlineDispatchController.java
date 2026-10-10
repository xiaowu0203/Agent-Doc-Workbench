package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.vo.OnlineTaskDispatchProofVO;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.common.utils.AuthUtils;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import com.agentdoc.task.service.TaskService;
import com.agentdoc.task.service.TaskOnlineDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/task/internal/online-dispatch")
public class OnlineDispatchController {
    private final TaskService tasks;
    private final TaskOnlineDispatchService dispatch;
    @GetMapping("/{taskId}/execution-identity")
    public Result<String> executionIdentity(@PathVariable String taskId, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (!AuthUtils.isAgent() || !authorization.startsWith("Bearer ")) { throw new BusinessException(ErrorCode.FORBIDDEN); }
        return Result.ok(tasks.verifyExecutionIdentity(OnlineProtocolUtils.id(taskId), authorization.substring("Bearer ".length())));
    }
    @RequireLogin
    @GetMapping("/{taskId}/human-proof")
    public Result<OnlineTaskDispatchProofVO> human(@PathVariable String taskId) {
        long id = OnlineProtocolUtils.id(taskId); tasks.authorizeOnlineCreationProof(id);
        return Result.ok(dispatch.humanProof(tasks.require(id)));
    }
    @GetMapping("/{taskId}/wait-proof")
    public Result<OnlineTaskDispatchProofVO> waitProof(@PathVariable String taskId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String wait) {
        return Result.ok(dispatch.waitProof(taskId, wait));
    }
}
