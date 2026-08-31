package com.agentdoc.task.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.task.pojo.dto.ChangeRequestReviewDTO;
import com.agentdoc.task.pojo.dto.ChangeRequestSubmitDTO;
import com.agentdoc.task.pojo.param.ChangeRequestSearchParam;
import com.agentdoc.task.pojo.vo.ChangeRequestVO;
import com.agentdoc.task.service.ChangeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "变更审批", description = "变更请求提交、审批队列、通过/拒绝/退回/合并")
@RestController
@RequestMapping("/api/task/change-requests")
@RequireLogin
@Validated
@RequiredArgsConstructor
public class ChangeRequestController {

    private final ChangeRequestService changeRequestService;

    @Operation(summary = "提交变更请求")
    @PostMapping
    public Result<ChangeRequestVO> submit(@Valid @RequestBody ChangeRequestSubmitDTO dto) {
        return Result.ok(changeRequestService.submit(dto));
    }

    @Operation(summary = "分页查询审批队列（可按空间 / 文档 / 状态过滤）")
    @PostMapping("/query")
    public Result<PageVO<ChangeRequestVO>> list(@Valid @RequestBody ChangeRequestSearchParam param) {
        return Result.ok(changeRequestService.list(param));
    }

    @Operation(summary = "审批通过")
    @PutMapping("/{id}/approve")
    public Result<ChangeRequestVO> approve(@PathVariable Long id, @Valid @RequestBody ChangeRequestReviewDTO dto) {
        return Result.ok(changeRequestService.approve(id, dto));
    }

    @Operation(summary = "审批拒绝")
    @PutMapping("/{id}/reject")
    public Result<ChangeRequestVO> reject(@PathVariable Long id, @Valid @RequestBody ChangeRequestReviewDTO dto) {
        return Result.ok(changeRequestService.reject(id, dto));
    }

    @Operation(summary = "批注退回（要求重改）")
    @PutMapping("/{id}/return")
    public Result<ChangeRequestVO> returnRequest(@PathVariable Long id,
                                                 @Valid @RequestBody ChangeRequestReviewDTO dto) {
        return Result.ok(changeRequestService.returnRequest(id, dto));
    }

    @Operation(summary = "合并变更（仅已通过可合并；版本不匹配报冲突）")
    @PutMapping("/{id}/merge")
    public Result<ChangeRequestVO> merge(@PathVariable Long id) {
        return Result.ok(changeRequestService.merge(id));
    }
}
