package com.agentdoc.document.controller;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import java.util.List;
import com.agentdoc.document.service.OnlineProtectionPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 已验签 CONTROL 复查当前授权人权限，不使用 RequireLogin。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/document/internal/online-authorizations")
public class OnlineProtectionPermissionController {
    private final OnlineProtectionPermissionService service;
    @GetMapping("/{experimentId}/resources")
    public Result<Void> resources(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token) { service.resources(experimentId, token); return Result.ok(); }
    @PostMapping("/{experimentId}/scoped-permission/{purpose}")
    public Result<Void> scoped(@PathVariable String experimentId, @PathVariable OnlineCapabilityPurpose purpose,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token, @RequestBody String json) {
        if (purpose == OnlineCapabilityPurpose.ONLINE_CONTROL) { throw new BusinessException(ErrorCode.BAD_REQUEST, OnlineReasonCode.BINDING_INVALID.name()); }
        List<String> ids;
        try { ids = OnlineCapabilityUtils.parseTaskIds(json); } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, OnlineReasonCode.ID_INVALID.name());
        }
        service.require(experimentId, token, purpose, ids); return Result.ok();
    }
    @RequireLogin
    @GetMapping("/spaces/{spaceId}/human-permission")
    public Result<Void> human(@PathVariable String spaceId) {
        service.requireHuman(spaceId);
        return Result.ok();
    }
    @GetMapping("/{experimentId}/permission")
    public Result<Void> permission(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String control) {
        service.require(experimentId, control);
        return Result.ok();
    }
}
