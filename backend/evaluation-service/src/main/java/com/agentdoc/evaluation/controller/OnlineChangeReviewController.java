package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.evaluation.pojo.vo.OnlineChangeReviewContextVO;
import com.agentdoc.evaluation.service.OnlineChangeReviewService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RequireLogin
@RestController
@RequiredArgsConstructor
public class OnlineChangeReviewController {
    private final OnlineChangeReviewService service;
    @Operation(summary = "读取隐藏分组的冻结输入及全部原始提案或草稿")
    @GetMapping("/api/evaluation/online-experiments/{id}/assignments/{assignmentId}/change-review-context")
    public ResponseEntity<Result<OnlineChangeReviewContextVO>> read(@PathVariable String id, @PathVariable String assignmentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(service.read(id, assignmentId)));
    }
}
