package com.agentdoc.agent.controller;

import com.agentdoc.agent.pojo.vo.SkillVersionVO;
import com.agentdoc.agent.service.SkillService;
import com.agentdoc.agent.service.SkillVersionService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Skill 版本管理", description = "Skill 版本上传、查询、下载与发布")
@RestController
@RequestMapping("/api/agent/skills-versions")
@RequireLogin
@RequiredArgsConstructor
public class SkillVersionController {

    private final SkillService skillService;
    private final SkillVersionService versionService;

    @Operation(summary = "上传 Skill 版本 ZIP（用户直接传ZIP、用户手写前端转为ZIP提交）")
    @PostMapping(value = "/{skillId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<SkillVersionVO> upload(@PathVariable Long skillId, @RequestPart("file") MultipartFile file) {
        return Result.ok(versionService.upload(skillId, file));
    }

    @Operation(summary = "查询 Skill 版本")
    @GetMapping("/{skillId}")
    public Result<List<SkillVersionVO>> list(@PathVariable Long skillId) {
        return Result.ok(versionService.list(skillId));
    }

    @Operation(summary = "查询 Skill 版本详情")
    @GetMapping("/{skillId}/{versionId}")
    public Result<SkillVersionVO> getVO(@PathVariable Long skillId, @PathVariable Long versionId) {
        return Result.ok(versionService.toVOForController(skillId, versionId));
    }

    @Operation(summary = "下载 Skill 版本 ZIP")
    @GetMapping("/{skillId}/{versionId}/package")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long skillId, @PathVariable Long versionId) {
        InputStream input = versionService.download(skillId, versionId);
        InputStreamResource resource = new InputStreamResource(input);
        String filename = skillService.require(skillId).getName() + "-" +
                versionService.detail(skillId, versionId).getVersionNo() + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
                        URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .body(resource);
    }

    @Operation(summary = "发布 Skill 版本")
    @PostMapping("/{skillId}/{versionId}/publish")
    public Result<SkillVersionVO> publish(@PathVariable Long skillId, @PathVariable Long versionId) {
        return Result.ok(versionService.publish(skillId, versionId));
    }
}
