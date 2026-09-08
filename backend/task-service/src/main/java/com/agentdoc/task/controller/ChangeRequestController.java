package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.task.pojo.dto.ChangeRequestReviewDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestApproveDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestBatchDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestCommentDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestSubmitDTO;
import com.agentdoc.task.pojo.param.ChangeRequestSearchParam;
import com.agentdoc.task.pojo.vo.ChangeRequestVO;
import com.agentdoc.task.pojo.vo.BatchChangeRequestResultVO;
import com.agentdoc.task.pojo.vo.ChangeRequestCommentVO;
import com.agentdoc.task.pojo.vo.ChangeRequestDetailVO;
import com.agentdoc.task.pojo.vo.ChangeRequestListItemVO;
import com.agentdoc.task.pojo.vo.PendingChangeStatsVO;
import com.agentdoc.task.service.ChangeRequestQueryService;
import com.agentdoc.task.service.ChangeRequestReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "变更审批", description = "变更请求提交、审批队列、通过/拒绝/退回/合并")
@RestController
@RequestMapping("/api/task/change-requests")
@RequireLogin
@Validated
@RequiredArgsConstructor
public class ChangeRequestController {

    private final ChangeRequestQueryService queryService;
    private final ChangeRequestReviewService reviewService;

    @Operation(summary = "提交变更请求")
    @PostMapping
    public Result<ChangeRequestVO> submit(@Valid @RequestBody ChangeRequestSubmitDTO dto) {
        return Result.ok(reviewService.submit(dto));
    }

    @Operation(summary = "分页查询审批队列（可按空间 / 文档 / 状态过滤）")
    @PostMapping("/query")
    public Result<PageVO<ChangeRequestListItemVO>> list(@Valid @RequestBody ChangeRequestSearchParam param) {
        return Result.ok(queryService.list(param));
    }

    @Operation(summary = "查询空间待审批变更数量统计")
    @GetMapping("/stats")
    public Result<PendingChangeStatsVO> stats(@RequestParam Long spaceId) {
        return Result.ok(queryService.getStats(spaceId));
    }

    @Operation(summary = "查询变更审批详情与 Diff 预览")
    @GetMapping("/{id}")
    public Result<ChangeRequestDetailVO> detail(@PathVariable Long id) {
        return Result.ok(queryService.detail(id));
    }

    @Operation(summary = "认领待审批请求")
    @PutMapping("/{id}/claim")
    public Result<ChangeRequestVO> claim(@PathVariable Long id) {
        return Result.ok(reviewService.claim(id));
    }

    @Operation(summary = "释放当前用户认领的待审批请求")
    @PutMapping("/{id}/unclaim")
    public Result<ChangeRequestVO> unclaim(@PathVariable Long id) {
        return Result.ok(reviewService.unclaim(id));
    }

    @Operation(summary = "审批通过")
    @PutMapping("/{id}/approve")
    public Result<ChangeRequestVO> approve(@PathVariable Long id,
                                           @Valid @RequestBody ChangeRequestApproveDTO dto) {
        return Result.ok(reviewService.approve(id, dto));
    }

    @Operation(summary = "通过并立即合并整单、部分或编辑后的变更")
    @PutMapping("/{id}/accept")
    public Result<ChangeRequestVO> accept(@PathVariable Long id,
                                          @Valid @RequestBody ChangeRequestApproveDTO dto) {
        reviewService.approve(id, dto);
        return Result.ok(reviewService.merge(id));
    }

    @Operation(summary = "审批拒绝")
    @PutMapping("/{id}/reject")
    public Result<ChangeRequestVO> reject(@PathVariable Long id,
                                          @Valid @RequestBody ChangeRequestReviewDTO dto) {
        return Result.ok(reviewService.reject(id, dto));
    }

    @Operation(summary = "批注退回（要求重改）")
    @PutMapping("/{id}/return")
    public Result<ChangeRequestVO> returnRequest(@PathVariable Long id,
                                                 @Valid @RequestBody ChangeRequestReviewDTO dto) {
        return Result.ok(reviewService.returnRequest(id, dto));
    }

    @Operation(summary = "合并变更（仅已通过可合并；版本不匹配报冲突）")
    @PutMapping("/{id}/merge")
    public Result<ChangeRequestVO> merge(@PathVariable Long id) {
        return Result.ok(reviewService.merge(id));
    }

    @Operation(summary = "新增整单或 Diff 块批注")
    @PostMapping("/{id}/comments")
    public Result<ChangeRequestCommentVO> addComment(@PathVariable Long id,
                                                     @Valid @RequestBody ChangeRequestCommentDTO dto) {
        return Result.ok(reviewService.addComment(id, dto));
    }

    @Operation(summary = "批量整单通过")
    @PutMapping("/batch/approve")
    public Result<BatchChangeRequestResultVO> batchApprove(@Valid @RequestBody ChangeRequestBatchDTO dto) {
        return Result.ok(reviewService.batchApprove(dto));
    }

    @Operation(summary = "批量整单通过并合并")
    @PutMapping("/batch/accept")
    public Result<BatchChangeRequestResultVO> batchAccept(@Valid @RequestBody ChangeRequestBatchDTO dto) {
        return Result.ok(reviewService.batchAccept(dto));
    }

    @Operation(summary = "批量拒绝")
    @PutMapping("/batch/reject")
    public Result<BatchChangeRequestResultVO> batchReject(@Valid @RequestBody ChangeRequestBatchDTO dto) {
        return Result.ok(reviewService.batchReject(dto));
    }

    @Operation(summary = "批量合并已通过请求")
    @PutMapping("/batch/merge")
    public Result<BatchChangeRequestResultVO> batchMerge(@Valid @RequestBody ChangeRequestBatchDTO dto) {
        return Result.ok(reviewService.batchMerge(dto));
    }
}
