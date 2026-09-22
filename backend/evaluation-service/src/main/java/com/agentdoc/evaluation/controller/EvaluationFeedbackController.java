package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.evaluation.pojo.dto.EvaluationFeedbackCreateDTO;
import com.agentdoc.evaluation.pojo.vo.EvaluationFeedbackVO;
import com.agentdoc.evaluation.service.EvaluationFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "人工评估反馈")
@RequireLogin
@RestController
@RequestMapping("/api/evaluation/feedback")
@RequiredArgsConstructor
public class EvaluationFeedbackController {
    private final EvaluationFeedbackService feedbackService;

    @Operation(summary = "提交不可变人工反馈")
    @PostMapping
    public Result<EvaluationFeedbackVO> create(@Valid @RequestBody EvaluationFeedbackCreateDTO request) {
        return Result.ok(feedbackService.create(request));
    }

    @Operation(summary = "导入 ChangeRequest 最终审批事实快照")
    @PostMapping("/import-change-request/{id}")
    public Result<EvaluationFeedbackVO> importChangeRequest(@PathVariable Long id) {
        return Result.ok(feedbackService.importChangeRequest(id));
    }
}
