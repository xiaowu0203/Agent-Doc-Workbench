package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.evaluation.pojo.dto.OnlineEvaluationCreateDTO;
import com.agentdoc.evaluation.pojo.vo.OnlineEvaluationVO;
import com.agentdoc.evaluation.service.OnlineEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireLogin
@RequiredArgsConstructor
public class OnlineEvaluationController {
    private final OnlineEvaluationService service;
    @Operation(summary = "追加冻结线上原始文本规则评价，不执行模型")
    @PostMapping("/api/evaluation/online-experiments/{id}/assignments/{assignmentId}/evaluations")
    public Result<OnlineEvaluationVO> evaluate(@PathVariable String id, @PathVariable String assignmentId, @RequestBody String json) {
        return Result.ok(service.evaluate(id, assignmentId, OnlineEvaluationCreateDTO.parse(json)));
    }
}
