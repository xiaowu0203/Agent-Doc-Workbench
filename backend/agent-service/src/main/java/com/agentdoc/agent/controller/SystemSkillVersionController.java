package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.vo.SkillVersionVO;
import com.agentdoc.agent.service.SkillVersionService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

@Tag(name = "系统 Skill 版本", description = "系统 Skill 不可变版本发布")
@RestController
@RequestMapping("/api/agent/system-skill-versions")
@RequireLogin
@RequiredArgsConstructor
public class SystemSkillVersionController {

    private final SkillVersionService versionService;

    @Operation(summary = "发布系统 Skill 版本")
    @PostMapping("/{versionId}/publish")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<SkillVersionVO> publish(@PathVariable Long versionId) {
        return Result.ok(versionService.publishSystem(versionId));
    }
}
