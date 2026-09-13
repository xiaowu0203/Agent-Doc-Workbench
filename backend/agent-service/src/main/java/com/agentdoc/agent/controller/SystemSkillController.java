package com.agentdoc.agent.controller;

import com.agentdoc.agent.enums.SkillStatus;
import com.agentdoc.agent.pojo.dto.SkillUpdateDTO;
import com.agentdoc.agent.pojo.dto.SystemSkillCreateDTO;
import com.agentdoc.agent.pojo.param.SystemSkillSearchParam;
import com.agentdoc.agent.pojo.vo.SkillVO;
import com.agentdoc.agent.pojo.vo.SkillImportVO;
import com.agentdoc.agent.pojo.vo.SkillAgentBindingVO;
import com.agentdoc.agent.pojo.vo.SkillVersionVO;
import com.agentdoc.agent.service.AgentSkillService;
import com.agentdoc.agent.service.SkillImportService;
import com.agentdoc.agent.service.SkillService;
import com.agentdoc.agent.service.SkillVersionService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.agentdoc.common.constant.PlatformRoleConstant.SUPER_ADMIN;

@Tag(name = "系统 Skill 目录", description = "系统 Skill 管理、版本上传与目录查询")
@RestController
@RequestMapping("/api/agent/system-skills")
@RequireLogin
@RequiredArgsConstructor
public class SystemSkillController {

    private final SkillService skillService;
    private final AgentSkillService agentSkillService;
    private final SkillVersionService versionService;
    private final SkillImportService skillImportService;

    @Operation(summary = "查询系统 Skill 目录")
    @PostMapping("/search")
    public Result<PageVO<SkillVO>> search(@Valid @RequestBody SystemSkillSearchParam param) {
        return Result.ok(skillService.listSystem(param));
    }

    @Operation(summary = "查询系统 Skill 详情")
    @GetMapping("/{skillId}")
    public Result<SkillVO> detail(@PathVariable Long skillId) {
        return Result.ok(skillService.toVO(skillService.detailSystem(skillId)));
    }

    @Operation(summary = "创建系统 Skill")
    @PostMapping
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<SkillVO> create(@Valid @RequestBody SystemSkillCreateDTO dto) {
        return Result.ok(skillService.toVO(skillService.createSystem(dto)));
    }

    @Operation(summary = "上传 ZIP 并自动创建系统 Skill 与首个草稿版本")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<SkillImportVO> importPackage(@RequestParam(required = false) String displayName,
                                                @RequestParam(required = false) String description,
                                                @RequestPart("file") MultipartFile file) {
        return Result.ok(skillImportService.importSystemPackage(displayName, description, file));
    }

    @Operation(summary = "更新系统 Skill")
    @PutMapping("/{skillId}")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<SkillVO> update(@PathVariable Long skillId, @Valid @RequestBody SkillUpdateDTO dto) {
        return Result.ok(skillService.toVO(skillService.updateSystem(skillId, dto)));
    }

    @Operation(summary = "上传系统 Skill 版本 ZIP")
    @PostMapping(value = "/{skillId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<SkillVersionVO> uploadVersion(@PathVariable Long skillId,
                                                @RequestPart("file") MultipartFile file) {
        return Result.ok(versionService.uploadSystem(skillId, file));
    }

    @Operation(summary = "查询系统 Skill 版本")
    @GetMapping("/{skillId}/versions")
    public Result<List<SkillVersionVO>> versions(@PathVariable Long skillId) {
        return Result.ok(versionService.listSystem(skillId));
    }

    @Operation(summary = "查询系统 Skill 版本详情")
    @GetMapping("/{skillId}/versions/{versionId}")
    public Result<SkillVersionVO> versionDetail(@PathVariable Long skillId, @PathVariable Long versionId) {
        return Result.ok(versionService.toSystemVOForController(skillId, versionId));
    }

    @Operation(summary = "查询系统 Skill 关联的 Agent")
    @GetMapping("/{skillId}/agents")
    public Result<List<SkillAgentBindingVO>> agents(@PathVariable Long skillId) {
        return Result.ok(agentSkillService.listBySkill(skillId));
    }

    @Operation(summary = "下载系统 Skill 版本 ZIP")
    @GetMapping("/{skillId}/versions/{versionId}/package")
    public ResponseEntity<InputStreamResource> downloadVersion(@PathVariable Long skillId,
                                                                @PathVariable Long versionId) {
        InputStream input = versionService.downloadSystem(skillId, versionId);
        String filename = skillService.requireSystem(skillId).getName() + "-" +
                versionService.detailSystem(skillId, versionId).getVersionNo() + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
                        URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .body(new InputStreamResource(input));
    }

    @Operation(summary = "停用系统 Skill")
    @PostMapping("/{skillId}/disable")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<Void> disable(@PathVariable Long skillId) {
        skillService.setSystemStatus(skillId, SkillStatus.DISABLED);
        return Result.ok();
    }

    @Operation(summary = "启用系统 Skill")
    @PostMapping("/{skillId}/enable")
    @PreAuthorize("@PlatformAccess.hasRole('" + SUPER_ADMIN + "')")
    public Result<Void> enable(@PathVariable Long skillId) {
        skillService.setSystemStatus(skillId, SkillStatus.ACTIVE);
        return Result.ok();
    }
}
