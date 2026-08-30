package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.AgentSkillReplaceDTO;
import com.agentdoc.agent.pojo.vo.AgentSkillBindingVO;
import com.agentdoc.agent.service.AgentSkillService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Agent Skill 绑定", description = "Agent 当前 Skill 版本绑定")
@RestController
@RequestMapping("/api/agent/agents")
@RequireLogin
@RequiredArgsConstructor
public class AgentSkillController {

    private final AgentSkillService agentSkillService;

    @Operation(summary = "查询 Agent Skill 绑定")
    @GetMapping("/{agentId}/skills")
    public Result<List<AgentSkillBindingVO>> list(@PathVariable Long agentId) {
        return Result.ok(agentSkillService.list(agentId));
    }

    @Operation(summary = "整体替换 Agent Skill 绑定")
    @PutMapping("/{agentId}/skills")
    public Result<List<AgentSkillBindingVO>> replace(@PathVariable Long agentId,
                                                     @Valid @RequestBody AgentSkillReplaceDTO dto) {
        return Result.ok(agentSkillService.replace(agentId, dto));
    }
}
