package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.OnlineExecutionObservationService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.vo.OnlineExecutionFactVO;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 专用服务入口，不允许普通登录或 Agent 凭证代替窄授权。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/internal/online-observation/{experimentId}")
public class OnlineExecutionObservationController {
    private final OnlineExecutionObservationService service;
    @PostMapping("/facts")
    public Result<List<OnlineExecutionFactVO>> facts(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token, @RequestBody String json) {
        return Result.ok(service.facts(experimentId, token, OnlineCapabilityUtils.parseTaskIds(json)));
    }
    @PostMapping("/cancel")
    public Result<Void> cancel(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token, @RequestBody String json) {
        service.cancel(experimentId, token, OnlineCapabilityUtils.parseTaskIds(json)); return Result.ok();
    }
}
