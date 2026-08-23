package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.document.pojo.vo.DocumentVersionDetailVO;
import com.agentdoc.document.pojo.vo.DocumentVersionVO;
import com.agentdoc.document.pojo.vo.VersionCompareVO;
import com.agentdoc.document.service.DocumentVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "文档版本", description = "版本列表、详情、对比")
@RestController
@RequestMapping("/api/document/documents/{id}/versions")
@RequireLogin
@Validated
@RequiredArgsConstructor
public class DocumentVersionController {

    private final DocumentVersionService versionService;

    @Operation(summary = "版本列表（分页，按版本号倒序）")
    @GetMapping
    public Result<PageVO<DocumentVersionVO>> list(@PathVariable Long id, PageParam pageParam) {
        return Result.ok(versionService.listVersions(id, pageParam));
    }

    @Operation(summary = "版本详情（含正文快照）")
    @GetMapping("/{versionNo}")
    public Result<DocumentVersionDetailVO> detail(@PathVariable Long id, @PathVariable Long versionNo) {
        return Result.ok(versionService.versionDetail(id, versionNo));
    }

    @Operation(summary = "版本对比（简化文本级，返回两版本快照）")
    @GetMapping("/compare")
    public Result<VersionCompareVO> compare(@PathVariable Long id,
                                            @RequestParam Long from,
                                            @RequestParam Long to) {
        return Result.ok(versionService.compare(id, from, to));
    }
}
