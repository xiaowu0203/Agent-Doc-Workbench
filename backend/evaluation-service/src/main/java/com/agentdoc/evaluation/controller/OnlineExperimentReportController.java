package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentDecisionDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineReportRecalculateDTO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentReportVO;
import com.agentdoc.evaluation.service.OnlineExperimentReportService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Set;

@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/evaluation/online-experiments/{id}")
public class OnlineExperimentReportController {
    private final OnlineExperimentReportService service;
    @GetMapping("/reports/{revision}")
    public Result<OnlineExperimentReportVO> report(@PathVariable String id, @PathVariable Integer revision) { return Result.ok(service.detail(id, revision)); }
    @GetMapping("/reports/{revision}/summary")
    public Result<OnlineExperimentReportVO> summary(@PathVariable String id, @PathVariable Integer revision) { return Result.ok(service.summary(id, revision)); }
    @PostMapping("/reports/recalculate")
    public Result<OnlineExperimentReportVO> recalculate(@PathVariable String id, @RequestBody String json) {
        var node = fields(json, Set.of("clientRequestKey", "assignmentCutoffSequence", "observationCutoffAt"));
        for (String field : Set.of("clientRequestKey", "assignmentCutoffSequence", "observationCutoffAt")) { if (!node.path(field).isTextual()) { throw invalid(); } }
        return Result.ok(service.recalculate(id, JsonUtils.parseStrict(json, OnlineReportRecalculateDTO.class)));
    }
    @PostMapping("/decision")
    public Result<Void> decide(@PathVariable String id, @RequestBody String json) {
        var node = fields(json, Set.of("clientRequestKey", "reportRevision", "reportHash", "decision", "reason", "safetyFeedbackId"));
        if (!node.path("reportRevision").isIntegralNumber() || !node.path("reportRevision").canConvertToInt()) { throw invalid(); }
        for (String field : Set.of("clientRequestKey", "reportHash", "decision", "reason")) { if (!node.path(field).isTextual()) { throw invalid(); } }
        if (node.hasNonNull("safetyFeedbackId") && !node.path("safetyFeedbackId").isTextual()) { throw invalid(); }
        service.decide(id, JsonUtils.parseStrict(json, OnlineExperimentDecisionDTO.class)); return Result.ok();
    }
    private static JsonNode fields(String json, Set<String> allowed) {
        try {
            var node = OnlineProtocolUtils.object(json); node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) { throw invalid(); } }); return node;
        } catch (RuntimeException malformed) { throw invalid(); }
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.BAD_REQUEST, "报告请求字段无效"); }
}
