package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentOnlineConfigService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.AgentOnlineConfigPrepareDTO;
import com.agentdoc.common.feign.vo.AgentOnlineConfigPairVO;
import com.agentdoc.common.feign.vo.AgentOnlinePromptComparisonVO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 只冻结配置；不提供执行入口。 */
@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/internal/online-configs")
public class AgentInternalOnlineConfigController {
    private final AgentOnlineConfigService service;

    @GetMapping("/{experimentId}/prompt-comparison")
    public ResponseEntity<Result<AgentOnlinePromptComparisonVO>> promptComparison(@PathVariable String experimentId, @RequestParam String spaceId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(service.promptComparison(experimentId, spaceId)));
    }

    @PostMapping("/prepare")
    public Result<AgentOnlineConfigPairVO> prepare(@RequestBody AgentOnlineConfigPrepareDTO request) {
        return Result.ok(service.prepare(request));
    }

    @GetMapping("/{experimentId}/dependency")
    public Result<String> dependency(@PathVariable String experimentId, @RequestParam Long spaceId) {
        return Result.ok(service.dependency(OnlineProtocolUtils.id(experimentId), spaceId));
    }
}
