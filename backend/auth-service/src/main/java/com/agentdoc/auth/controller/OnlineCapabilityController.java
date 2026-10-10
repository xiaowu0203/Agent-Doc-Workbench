package com.agentdoc.auth.controller;

import com.agentdoc.auth.service.OnlineCapabilityIssuanceService;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.dto.OnlineCapabilityIssueDTO;
import com.agentdoc.common.feign.dto.OnlineControlAuthorizeDTO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.JsonUtils;
import com.agentdoc.common.utils.OnlineProtocolUtils;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OnlineCapabilityController {
    private final OnlineCapabilityIssuanceService service;
    @RequireLogin
    @Operation(summary = "OWNER 明确确认线上自动保护授权")
    @PostMapping("/api/auth/internal/online-control-authorizations")
    public Result<String> authorize(@RequestBody String json) {
        JsonNode node = object(json);
        requireId(node.path("experimentId"));
        if (!node.path("manifestHash").isTextual() || !node.path("automaticCancellationAcknowledged").isBoolean()) {
            throw bad(OnlineReasonCode.MANIFEST_INVALID);
        }
        var request = JsonUtils.parseStrict(json, OnlineControlAuthorizeDTO.class);
        if (request == null) { throw bad(OnlineReasonCode.MANIFEST_INVALID); }
        return Result.ok(service.authorize(request));
    }
    @Operation(summary = "内部续签同实验 CONTROL 或派生既存任务窄凭证")
    @PostMapping("/api/auth/internal/online-capabilities")
    public Result<String> issue(@RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String control,
            @RequestBody String json) {
        JsonNode node = object(json);
        if (!node.path("purpose").isTextual()) { throw bad(OnlineReasonCode.MANIFEST_INVALID); }
        if (node.hasNonNull("taskIds")) {
            JsonNode ids = node.get("taskIds");
            if (!ids.isArray() || ids.size() > OnlineCapabilityConstant.MAX_TASK_COUNT) { throw bad(OnlineReasonCode.ID_INVALID); }
            ids.forEach(this::requireId);
        }
        var request = JsonUtils.parseStrict(json, OnlineCapabilityIssueDTO.class);
        if (request == null) { throw bad(OnlineReasonCode.MANIFEST_INVALID); }
        return Result.ok(service.issue(control, request));
    }

    private JsonNode object(String json) {
        try { return OnlineProtocolUtils.object(json); }
        catch (IllegalArgumentException invalid) { throw bad(OnlineReasonCode.MANIFEST_INVALID); }
    }
    private void requireId(JsonNode node) {
        if (!node.isTextual()) { throw bad(OnlineReasonCode.ID_INVALID); }
        try { OnlineProtocolUtils.id(node.textValue()); }
        catch (IllegalArgumentException invalid) { throw bad(OnlineReasonCode.ID_INVALID); }
    }
    private BusinessException bad(OnlineReasonCode reason) { return new BusinessException(ErrorCode.BAD_REQUEST, reason.name()); }
}
