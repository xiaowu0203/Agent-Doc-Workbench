package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.MergeRequestDTO;
import com.agentdoc.common.feign.vo.DocumentRefVO;
import com.agentdoc.common.feign.vo.MergeResultVO;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.document.constant.DocumentConstant;
import com.agentdoc.document.pojo.dto.DocumentCreateDTO;
import com.agentdoc.document.pojo.dto.DocumentDraftSaveDTO;
import com.agentdoc.document.pojo.dto.DocumentMoveDTO;
import com.agentdoc.document.pojo.dto.DocumentUpdateDTO;
import com.agentdoc.document.pojo.param.DocumentRecentSearchParam;
import com.agentdoc.document.pojo.param.DocumentTreeSearchParam;
import com.agentdoc.document.pojo.vo.DocumentDetailVO;
import com.agentdoc.document.pojo.vo.DocumentDraftVO;
import com.agentdoc.common.feign.vo.DocumentExecutionContextVO;
import com.agentdoc.document.pojo.vo.DocumentFragmentVO;
import com.agentdoc.document.pojo.vo.RecentDocumentVO;
import com.agentdoc.document.pojo.vo.DocumentTreeNodeVO;
import com.agentdoc.document.pojo.vo.DocumentVO;
import com.agentdoc.document.service.DocumentService;
import com.agentdoc.document.service.DocumentDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "文档管理", description = "文档 CRUD、树形目录、归档回收站、版本回滚")
@RestController
@RequestMapping("/api/document/documents")
@RequireLogin
@Validated
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentDraftService documentDraftService;

    @Operation(summary = "创建文档")
    @PostMapping
    @PreAuthorize("@SpacePermission.hasPermission(#dto.spaceId(), '" + com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_CREATE + "')")
    public Result<DocumentVO> create(@Valid @RequestBody DocumentCreateDTO dto) {
        return Result.ok(documentService.create(dto));
    }

    @Operation(summary = "合并变更至正式文档（服务间调用）")
    @PostMapping("/merge")
    public Result<MergeResultVO> merge(@RequestBody MergeRequestDTO request) {
        return Result.ok(documentService.mergeForFeign(request));
    }

    @Operation(summary = "Agent 直接更新草稿文档（服务间调用）")
    @PostMapping("/draft-agent-apply")
    public Result<MergeResultVO> applyDraftAgent(@RequestBody MergeRequestDTO request) {
        return Result.ok(documentService.applyAgentDraftChanges(request));
    }

    @Operation(summary = "文档引用批量查询（服务间调用）")
    @GetMapping("/refs")
    public Result<List<DocumentRefVO>> listRefs(@RequestParam List<Long> documentIds) {
        return Result.ok(documentService.listRefs(documentIds));
    }

    @Operation(summary = "空间文档 ID 列表（服务间调用）")
    @GetMapping("/ids")
    public Result<List<Long>> listIdsBySpace(@RequestParam Long spaceId) {
        return Result.ok(documentService.listIdsBySpace(spaceId));
    }

    @Operation(summary = "查询最近文档")
    @PostMapping("/recent/query")
    public Result<PageVO<RecentDocumentVO>> listRecent(@Valid @RequestBody DocumentRecentSearchParam param) {
        return Result.ok(documentService.listRecent(param));
    }

    @Operation(summary = "文档树查询")
    @PostMapping("/tree")
    @PreAuthorize("@SpacePermission.hasPermission(#param.spaceId(), '" + com.agentdoc.common.constant.SpacePermissionConstant.DOCUMENT_READ + "')")
    public Result<List<DocumentTreeNodeVO>> listTree(@Valid @RequestBody DocumentTreeSearchParam param) {
        return Result.ok(documentService.listTree(param));
    }

    @Operation(summary = "回收站列表")
    @GetMapping("/trash")
    public Result<PageVO<DocumentVO>> trashList(@RequestParam Long spaceId, PageParam pageParam) {
        return Result.ok(documentService.trashList(spaceId, pageParam));
    }

    @Operation(summary = "文档片段读取（按偏移，供 Agent 按需加载控 Token，服务间调用）")
    @GetMapping("/{id}/fragments")
    public Result<DocumentFragmentVO> readFragment(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "0") long start,
                                                   @RequestParam(defaultValue = DocumentConstant.DEFAULT_FRAGMENT_LENGTH)
                                                   @Max(value = DocumentConstant.FRAGMENT_MAX_LENGTH,
                                                           message = "单次读取长度不能超过 " + DocumentConstant.FRAGMENT_MAX_LENGTH)
                                                   int length) {
        return Result.ok(documentService.readFragment(id, start, length));
    }

    @Operation(summary = "查询 Agent 任务执行上下文（服务间调用）")
    @GetMapping("/{id}/execution-context")
    public Result<DocumentExecutionContextVO> executionContext(@PathVariable Long id) {
        return Result.ok(documentService.getExecutionContext(id));
    }

    @Operation(summary = "文档详情（含正文）")
    @GetMapping("/{id}")
    public Result<DocumentDetailVO> detail(@PathVariable Long id) {
        return Result.ok(documentService.detail(id));
    }

    @Operation(summary = "查询文档未提交草稿")
    @GetMapping("/{id}/draft")
    public Result<DocumentDraftVO> draft(@PathVariable Long id) {
        return Result.ok(documentDraftService.get(id));
    }

    @Operation(summary = "保存文档未提交草稿")
    @PutMapping("/{id}/draft")
    public Result<DocumentDraftVO> saveDraft(@PathVariable Long id,
                                             @Valid @RequestBody DocumentDraftSaveDTO dto) {
        return Result.ok(documentDraftService.save(id, dto));
    }

    @Operation(summary = "删除文档未提交草稿")
    @DeleteMapping("/{id}/draft")
    public Result<Void> deleteDraft(@PathVariable Long id) {
        documentDraftService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "更新文档（内容变化自动生成版本快照）")
    @PutMapping("/{id}")
    public Result<DocumentDetailVO> update(@PathVariable Long id, @Valid @RequestBody DocumentUpdateDTO dto) {
        return Result.ok(documentService.update(id, dto));
    }

    @Operation(summary = "移动文档")
    @PutMapping("/{id}/move")
    public Result<DocumentVO> move(@PathVariable Long id, @Valid @RequestBody DocumentMoveDTO dto) {
        return Result.ok(documentService.move(id, dto));
    }

    @Operation(summary = "归档文档（进入回收站）")
    @PutMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        documentService.archive(id);
        return Result.ok();
    }

    @Operation(summary = "恢复文档")
    @PutMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        documentService.restore(id);
        return Result.ok();
    }

    @Operation(summary = "回滚文档版本（生成新版本，不删历史快照）")
    @PutMapping("/{id}/rollback")
    public Result<DocumentDetailVO> rollback(@PathVariable Long id, @RequestParam Long versionNo) {
        return Result.ok(documentService.rollback(id, versionNo));
    }
}
