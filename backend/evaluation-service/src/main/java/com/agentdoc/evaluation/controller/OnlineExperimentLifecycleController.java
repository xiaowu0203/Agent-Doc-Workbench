package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.agentdoc.evaluation.enums.OnlineExperimentAction;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentStartDTO;
import com.agentdoc.evaluation.pojo.dto.OnlineExperimentStateDTO;
import com.agentdoc.evaluation.pojo.vo.OnlineExperimentActionVO;
import com.agentdoc.evaluation.service.OnlineExperimentLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 公共状态操作只接受当前人类身份；完整预检尚未就绪时拒绝 start/resume。 */
@RestController
@RequireLogin
@RequiredArgsConstructor
@RequestMapping("/api/evaluation/online-experiments")
public class OnlineExperimentLifecycleController {
    private final OnlineExperimentLifecycleService service;

    @Operation(summary = "完整预检后开始受控线上实验")
    @PostMapping("/{id}/start")
    public Result<OnlineExperimentActionVO> start(@PathVariable String id, @RequestBody String json) {
        var node = object(json);
        for (String name : List.of("clientRequestKey", "manifestHash", "expectedStateVersion", "preflightProofHash")) { text(node, name); }
        for (String name : List.of("liveSideEffectsAcknowledged", "retentionAcknowledged", "sharedResourcesAcknowledged", "emergencyCancellationAcknowledged")) {
            if (!node.path(name).isBoolean()) { throw invalid(); }
        }
        return Result.ok(service.start(id, parse(json, OnlineExperimentStartDTO.class)));
    }

    @Operation(summary = "暂停、恢复、停止、紧急停止或明确重新授权")
    @PostMapping("/{id}/{action:pause|resume|stop|emergency-stop|reauthorize}")
    public Result<OnlineExperimentActionVO> state(@PathVariable String id, @PathVariable String action, @RequestBody String json) {
        var node = object(json);
        for (String name : List.of("clientRequestKey", "manifestHash", "expectedStateVersion", "reason")) { text(node, name); }
        var type = switch (action) {
            case "pause" -> OnlineExperimentAction.PAUSE;
            case "resume" -> OnlineExperimentAction.RESUME;
            case "stop" -> OnlineExperimentAction.STOP;
            case "emergency-stop" -> OnlineExperimentAction.EMERGENCY_STOP;
            case "reauthorize" -> OnlineExperimentAction.REAUTHORIZE;
            default -> throw invalid();
        };
        return Result.ok(service.state(id, type, parse(json, OnlineExperimentStateDTO.class)));
    }

    private static JsonNode object(String json) {
        try { return OnlineProtocolUtils.object(json); } catch (IllegalArgumentException invalid) { throw invalid(); }
    }
    private static void text(JsonNode node, String name) { if (!node.path(name).isTextual()) { throw invalid(); } }
    private static <T> T parse(String json, Class<T> type) {
        T value = JsonUtils.parseStrict(json, type); if (value == null) { throw invalid(); } return value;
    }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.BAD_REQUEST, "MANIFEST_INVALID"); }
}
