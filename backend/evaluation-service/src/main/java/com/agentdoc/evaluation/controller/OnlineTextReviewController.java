package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.evaluation.pojo.vo.OnlineTextReviewContextVO;
import com.agentdoc.evaluation.service.OnlineTextReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequireLogin
@RestController
@RequiredArgsConstructor
@Tag(name = "原始产物评审", description = "隐藏分组；知情记录缺失仍为UNKNOWN")
@RequestMapping("/api/evaluation/online-experiments/{id}/assignments/{assignmentId}")
public class OnlineTextReviewController {
    private final OnlineTextReviewService service;
    @Operation(summary = "读取隐藏分组的冻结输入及原始最终文本")
    @GetMapping("/text-review-context")
    public ResponseEntity<Result<OnlineTextReviewContextVO>> read(@PathVariable String id, @PathVariable String assignmentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(service.read(id, assignmentId)));
    }
}
