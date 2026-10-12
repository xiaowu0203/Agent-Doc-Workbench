package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.pojo.dto.PageParam;
import com.agentdoc.common.pojo.vo.PageVO;
import com.agentdoc.evaluation.pojo.vo.OnlineReviewAssignmentVO;
import com.agentdoc.evaluation.service.OnlineBlindReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/evaluation/online-experiments/{id}/review-assignments")
public class OnlineReviewAssignmentController {
    private final OnlineBlindReviewService service;
    @PostMapping("/search")
    public Result<PageVO<OnlineReviewAssignmentVO>> search(@PathVariable String id, @RequestBody PageParam page) { return Result.ok(service.assignments(id, page)); }
}
