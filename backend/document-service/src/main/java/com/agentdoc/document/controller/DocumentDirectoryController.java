package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.document.pojo.dto.DirectoryCreateDTO;
import com.agentdoc.document.pojo.dto.DirectoryMoveDTO;
import com.agentdoc.document.pojo.dto.DirectoryUpdateDTO;
import com.agentdoc.document.pojo.vo.DocumentDirectoryVO;
import com.agentdoc.document.service.DocumentDirectoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档目录接口。
 */
@Tag(name = "文档目录管理", description = "纯目录树的创建、归档和恢复")
@RestController
@RequestMapping("/api/document/directories")
@RequireLogin
@RequiredArgsConstructor
public class DocumentDirectoryController {

    private final DocumentDirectoryService directoryService;

    @Operation(summary = "创建文档目录")
    @PostMapping
    @PreAuthorize("@SpacePermission.hasPermission(#dto.spaceId(), '" + com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_CREATE + "')")
    public Result<DocumentDirectoryVO> create(@Valid @RequestBody DirectoryCreateDTO dto) {
        return Result.ok(directoryService.create(dto));
    }

    @Operation(summary = "移动文档目录")
    @PutMapping("/{id}/move")
    public Result<DocumentDirectoryVO> move(@PathVariable Long id, @RequestBody DirectoryMoveDTO dto) {
        return Result.ok(directoryService.move(id, dto));
    }

    @Operation(summary = "更新文档目录名称")
    @PutMapping("/{id}")
    public Result<DocumentDirectoryVO> update(@PathVariable Long id,
                                              @Valid @RequestBody DirectoryUpdateDTO dto) {
        return Result.ok(directoryService.update(id, dto));
    }

    @Operation(summary = "归档文档目录")
    @PutMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        directoryService.archive(id);
        return Result.ok();
    }

    @Operation(summary = "恢复文档目录")
    @PutMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        directoryService.restore(id);
        return Result.ok();
    }
}
