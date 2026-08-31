package com.agentdoc.agent.controller;

import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.pojo.dto.SkillCreateDTO;
import com.agentdoc.agent.pojo.dto.SkillUpdateDTO;
import com.agentdoc.agent.pojo.param.SkillSearchParam;
import com.agentdoc.agent.pojo.vo.SkillVO;
import com.agentdoc.agent.service.SkillService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Skill 管理", description = "Skill 元数据管理")
@RestController
@RequestMapping("/api/agent/skills")
@RequireLogin
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @Operation(summary = "创建 Skill")
    @PostMapping
    @PreAuthorize("@SpaceAccess.hasPermission(#dto.spaceId(), '" + com.agentdoc.common.constant.SpacePermissionConstant.SKILL_MANAGE + "')")
    public Result<SkillVO> create(@Valid @RequestBody SkillCreateDTO dto) {
        return Result.ok(skillService.toVO(skillService.create(dto)));
    }

    @Operation(summary = "查询 Skill 列表")
    @PostMapping("/search")
    public Result<PageVO<SkillVO>> list(@Valid @RequestBody SkillSearchParam param) {
        return Result.ok(skillService.list(param));
    }

    @Operation(summary = "查询 Skill 详情")
    @GetMapping("/{skillId}")
    public Result<SkillVO> detail(@PathVariable Long skillId) {
        return Result.ok(skillService.toVO(skillService.detail(skillId)));
    }

    @Operation(summary = "更新 Skill")
    @PutMapping("/{skillId}")
    public Result<SkillVO> update(@PathVariable Long skillId, @Valid @RequestBody SkillUpdateDTO dto) {
        return Result.ok(skillService.toVO(skillService.update(skillId, dto)));
    }

    @Operation(summary = "停用 Skill")
    @PostMapping("/{skillId}/disable")
    public Result<Void> disable(@PathVariable Long skillId) {
        skillService.setStatus(skillId, SkillStatus.DISABLED);
        return Result.ok();
    }

    @Operation(summary = "启用 Skill")
    @PostMapping("/{skillId}/enable")
    public Result<Void> enable(@PathVariable Long skillId) {
        skillService.setStatus(skillId, SkillStatus.ACTIVE);
        return Result.ok();
    }

}
