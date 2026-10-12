package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.OnlineReviewExposureDTO;
import com.agentdoc.evaluation.service.OnlineRelatedExposureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/evaluation/internal/online-review")
public class OnlineRelatedExposureController {
    private final OnlineRelatedExposureService service;
    @PostMapping("/exposures")
    public Result<Void> record(@RequestBody OnlineReviewExposureDTO request) { service.record(request); return Result.ok(); }
}
