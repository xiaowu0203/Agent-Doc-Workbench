package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.dto.SpaceSkillInstallationCreateDTO;
import com.agentdoc.agent.pojo.dto.SpaceSkillInstallationUpdateDTO;
import com.agentdoc.agent.pojo.vo.SpaceSkillInstallationVO;
import com.agentdoc.agent.service.SpaceSkillInstallationService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "空间系统 Skill 安装", description = "安装、升级、启停和卸载系统 Skill")
@RestController
@RequestMapping("/api/agent/spaces/{spaceId}/skill-installations")
@RequireLogin
@RequiredArgsConstructor
public class SpaceSkillInstallationController {

    private final SpaceSkillInstallationService installationService;

    @Operation(summary = "查询空间已安装的系统 Skill")
    @GetMapping
    public Result<List<SpaceSkillInstallationVO>> list(@PathVariable Long spaceId) {
        return Result.ok(installationService.list(spaceId));
    }

    @Operation(summary = "安装系统 Skill")
    @PostMapping
    public Result<SpaceSkillInstallationVO> install(@PathVariable Long spaceId,
                                                    @Valid @RequestBody SpaceSkillInstallationCreateDTO dto) {
        return Result.ok(installationService.install(spaceId, dto));
    }

    @Operation(summary = "升级或启停已安装系统 Skill")
    @PutMapping("/{installationId}")
    public Result<SpaceSkillInstallationVO> update(@PathVariable Long spaceId,
                                                   @PathVariable Long installationId,
                                                   @Valid @RequestBody SpaceSkillInstallationUpdateDTO dto) {
        return Result.ok(installationService.update(spaceId, installationId, dto));
    }

    @Operation(summary = "卸载系统 Skill")
    @DeleteMapping("/{installationId}")
    public Result<Void> uninstall(@PathVariable Long spaceId, @PathVariable Long installationId) {
        installationService.uninstall(spaceId, installationId);
        return Result.ok();
    }
}
