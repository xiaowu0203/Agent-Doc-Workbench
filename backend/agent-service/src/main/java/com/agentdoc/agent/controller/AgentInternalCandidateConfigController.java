package com.agentdoc.agent.controller;

import com.agentdoc.agent.service.AgentCandidateConfigService;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.AgentCandidateConfigCreateDTO;
import com.agentdoc.common.feign.vo.AgentCandidateConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent 候选配置内部接口")
@RestController
@RequestMapping("/api/agent/internal/candidate-configs")
@RequiredArgsConstructor
public class AgentInternalCandidateConfigController {

    private final AgentCandidateConfigService candidateConfigService;

    @Operation(summary = "创建不可变 Prompt 候选配置")
    @PostMapping
    public Result<AgentCandidateConfigVO> create(@RequestBody AgentCandidateConfigCreateDTO request) {
        return Result.ok(candidateConfigService.create(request));
    }

    @Operation(summary = "恢复校验不可变 Prompt 候选配置身份")
    @GetMapping("/{id}/identity")
    public Result<AgentCandidateConfigVO> identity(@PathVariable Long id,
                                                    @RequestParam Long spaceId,
                                                    @RequestParam String candidateSnapshotHash) {
        return Result.ok(candidateConfigService.requireRestorableIdentity(
                id, spaceId, candidateSnapshotHash));
    }
}
