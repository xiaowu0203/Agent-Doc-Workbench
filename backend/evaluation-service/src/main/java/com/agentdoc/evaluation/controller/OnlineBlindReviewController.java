package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.evaluation.pojo.vo.OnlineBlindReviewContextVO;
import com.agentdoc.evaluation.service.OnlineBlindReviewService;
import com.agentdoc.evaluation.service.OnlineQualityHistoryService;
import com.agentdoc.evaluation.pojo.param.OnlineQualityHistorySearchParam;
import com.agentdoc.evaluation.pojo.vo.OnlineQualityHistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/evaluation/online-experiments/{id}/assignments/{assignmentId}")
public class OnlineBlindReviewController {
    private final OnlineBlindReviewService service;
    private final OnlineQualityHistoryService history;
    @PostMapping("/quality-feedback-history/search")
    public ResponseEntity<Result<OnlineQualityHistoryVO>> history(@PathVariable String id, @PathVariable String assignmentId,
            @RequestBody OnlineQualityHistorySearchParam param) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(history.search(id, assignmentId, param)));
    }
    @GetMapping("/blind-review-context")
    public ResponseEntity<Result<OnlineBlindReviewContextVO>> read(@PathVariable String id, @PathVariable String assignmentId, @RequestParam String target) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Result.ok(service.read(id, assignmentId, target)));
    }
}
