package com.agentdoc.evaluation.controller;

import com.agentdoc.common.annotation.RequireLogin;
import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.vo.OnlineAuthorizationProofVO;
import com.agentdoc.common.feign.vo.OnlineResourceScopeVO;
import com.agentdoc.common.enums.ErrorCode;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.enums.OnlineReasonCode;
import com.agentdoc.common.exception.BusinessException;
import com.agentdoc.common.utils.OnlineCapabilityUtils;
import com.agentdoc.evaluation.service.OnlineAuthorizationProofService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 内部只读证明；专用凭证不转换为人类登录。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/evaluation/internal/online-authorizations")
public class OnlineAuthorizationProofController {
    private final OnlineAuthorizationProofService service;
    @GetMapping("/{experimentId}/resource-scope")
    public Result<OnlineResourceScopeVO> scope(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token) { return Result.ok(service.resourceScope(experimentId, token)); }
    @RequireLogin
    @GetMapping("/{experimentId}/human-proof")
    public Result<OnlineAuthorizationProofVO> human(@PathVariable String experimentId) { return Result.ok(service.human(experimentId)); }
    @GetMapping("/{experimentId}/control-proof")
    public Result<OnlineAuthorizationProofVO> control(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token) { return Result.ok(service.control(experimentId, token)); }
    @PostMapping("/{experimentId}/bindings")
    public Result<List<OnlineTaskBindingDTO>> bindings(@PathVariable String experimentId,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token, @RequestBody String json) {
        List<String> ids;
        try { ids = OnlineCapabilityUtils.parseTaskIds(json); }
        catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.BAD_REQUEST, OnlineReasonCode.ID_INVALID.name()); }
        return Result.ok(service.bindings(experimentId, token, ids));
    }

    @PostMapping("/{experimentId}/scoped-bindings/{purpose}")
    public Result<List<OnlineTaskBindingDTO>> scopedBindings(@PathVariable String experimentId, @PathVariable OnlineCapabilityPurpose purpose,
            @RequestHeader(value = OnlineCapabilityConstant.HEADER, required = false) String token, @RequestBody String json) {
        List<String> ids;
        try { ids = OnlineCapabilityUtils.parseTaskIds(json); }
        catch (IllegalArgumentException invalid) { throw new BusinessException(ErrorCode.BAD_REQUEST, OnlineReasonCode.ID_INVALID.name()); }
        return Result.ok(service.scopedBindings(experimentId, token, purpose, ids));
    }
}
