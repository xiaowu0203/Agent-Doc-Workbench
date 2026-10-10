package com.agentdoc.task.controller;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.vo.OnlineTaskFactVO;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.task.service.TaskOnlineObservationService;
import com.agentdoc.task.service.TaskOnlineCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 服务事实与取消请求；严格批次授权，不接受外部自报终态/费用。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/task/internal/online-observation/{experimentId}")
public class OnlineObservationController {
    private final TaskOnlineObservationService service;
    private final TaskOnlineCompensationService compensation;
    @PostMapping("/repair")
    public Result<Void> repair(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token, @RequestBody String json) {
        compensation.repair(experimentId, token, OnlineCapabilityUtils.parseTaskIds(json)); return Result.ok();
    }
    @PostMapping("/facts")
    public Result<List<OnlineTaskFactVO>> facts(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token, @RequestBody String json) {
        return Result.ok(service.facts(experimentId, token, OnlineCapabilityUtils.parseTaskIds(json)));
    }
    @PostMapping("/cancel")
    public Result<Void> cancel(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token, @RequestBody String json) {
        service.cancel(experimentId, token, OnlineCapabilityUtils.parseTaskIds(json)); return Result.ok();
    }
}
