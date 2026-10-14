package com.agentdoc.document.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.OnlineProposalPreviewBatchDTO;
import com.agentdoc.common.feign.vo.OnlineChangePreviewVO;
import com.agentdoc.common.feign.vo.OnlineOriginalChangesVO;
import com.agentdoc.common.feign.vo.OnlineApprovalDifferenceVO;
import com.agentdoc.common.feign.vo.OnlineProposalReviewPreviewVO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import com.agentdoc.document.service.DocumentOnlineApprovalDifferenceService;
import com.agentdoc.document.service.DocumentOnlineOriginalDraftService;
import com.agentdoc.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequireLogin
@RequiredArgsConstructor
public class DocumentOnlineOriginalChangeController {
    private final DocumentService documents;
    private final DocumentOnlineOriginalDraftService drafts;
    private final DocumentOnlineApprovalDifferenceService approvals;
    @Operation(summary = "完整预览全部原始提案正文，仅只读计算")
    @PostMapping("/api/document/documents/online-change-review-preview")
    public ResponseEntity<Result<OnlineProposalReviewPreviewVO>> review(@RequestBody OnlineProposalPreviewBatchDTO request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(documents.previewOnlineChangesForReview(request)));
    }
    @Operation(summary = "只读比较线上原始提案与审批合并快照")
    @GetMapping("/api/document/documents/online-approval-differences/{changeRequestId}")
    public Result<OnlineApprovalDifferenceVO> approval(@PathVariable Long changeRequestId) {
        return Result.ok(approvals.get(changeRequestId));
    }
    @Operation(summary = "批量只读校验审批前的原始提案")
    @PostMapping("/api/document/documents/online-change-preview")
    public Result<List<OnlineChangePreviewVO>> preview(@RequestBody OnlineProposalPreviewBatchDTO request) {
        return Result.ok(documents.previewOnlineChanges(request));
    }
    @Operation(summary = "读取已授权任务的原始草稿收尾事实")
    @GetMapping("/api/document/documents/online-original-drafts/tasks/{taskId}")
    public Result<OnlineOriginalChangesVO> read(@PathVariable Long taskId, @RequestParam Long spaceId, @RequestParam Long executionId) {
        return Result.ok(drafts.read(taskId, spaceId, executionId));
    }
}
