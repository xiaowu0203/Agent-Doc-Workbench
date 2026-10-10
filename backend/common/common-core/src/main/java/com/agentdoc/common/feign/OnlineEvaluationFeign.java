package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.enums.OnlineCapabilityPurpose;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.dto.OnlineTaskBindingDTO;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.feign.vo.OnlineRouteVO;
import com.agentdoc.common.feign.vo.OnlineSlotPermitVO;
import com.agentdoc.common.feign.vo.OnlineAuthorizationProofVO;
import com.agentdoc.common.feign.vo.OnlineResourceScopeVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.HttpHeaders;

/** 专用直连 Evaluation 权威只读证明，不通过公共 Gateway。 */
@FeignClient(name = "online-evaluation", url = "${agent-doc.online.evaluation-url:http://localhost:8085}")
public interface OnlineEvaluationFeign {
    @GetMapping("/api/evaluation/internal/online-authorizations/{experimentId}/resource-scope")
    Result<OnlineResourceScopeVO> resourceScope(@PathVariable String experimentId, @RequestHeader(OnlineCapabilityConstant.HEADER) String control);
    @PostMapping("/api/evaluation/internal/online-authorizations/{experimentId}/scoped-bindings/{purpose}")
    Result<List<OnlineTaskBindingDTO>> scopedBindings(@PathVariable String experimentId, @PathVariable OnlineCapabilityPurpose purpose,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String token, @RequestBody List<String> taskIds);
    @PostMapping("/api/evaluation/internal/online-assignments/route")
    Result<OnlineRouteVO> route(@RequestBody OnlineAssignmentRequestDTO request);
    @PostMapping("/api/evaluation/internal/online-assignments/check-scope")
    Result<Boolean> checkScope(@RequestBody OnlineAssignmentRequestDTO request);
    @GetMapping("/api/evaluation/internal/online-assignments/{taskId}/human-binding")
    Result<OnlineTaskBindingDTO> humanBinding(@PathVariable String taskId);
    @GetMapping("/api/evaluation/internal/online-assignments/{taskId}/binding")
    Result<OnlineTaskBindingDTO> waitBinding(@PathVariable String taskId, @RequestHeader(OnlineCapabilityConstant.HEADER) String wait);
    @GetMapping("/api/evaluation/internal/online-assignments/{taskId}/waiting")
    Result<OnlineTaskBindingDTO> waiting(@PathVariable String taskId, @RequestHeader(OnlineCapabilityConstant.HEADER) String wait);
    @PostMapping("/api/evaluation/internal/online-assignments/{taskId}/claim")
    Result<OnlineSlotPermitVO> claim(@PathVariable String taskId, @RequestHeader(OnlineCapabilityConstant.HEADER) String wait);
    @GetMapping("/api/evaluation/internal/online-assignments/{taskId}/permit")
    Result<OnlineSlotPermitVO> permit(@PathVariable String taskId, @RequestHeader(OnlineCapabilityConstant.HEADER) String wait);
    @GetMapping("/api/evaluation/internal/online-assignments/{taskId}/execution-permit")
    Result<OnlineSlotPermitVO> executionPermit(@PathVariable String taskId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
    @PostMapping("/api/evaluation/internal/online-assignments/{taskId}/begin")
    Result<OnlineSlotPermitVO> begin(@PathVariable String taskId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
    /** 人类初次授权：当前用户凭证由既有拦截器透传。 */
    @GetMapping("/api/evaluation/internal/online-authorizations/{experimentId}/human-proof")
    Result<OnlineAuthorizationProofVO> humanProof(@PathVariable String experimentId);
    /** 续签：只携带固定范围 CONTROL。 */
    @GetMapping("/api/evaluation/internal/online-authorizations/{experimentId}/control-proof")
    Result<OnlineAuthorizationProofVO> controlProof(@PathVariable String experimentId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String control);
    /** 只查询已接受的 Task，整批不完整即失败。 */
    @PostMapping("/api/evaluation/internal/online-authorizations/{experimentId}/bindings")
    Result<List<OnlineTaskBindingDTO>> bindings(@PathVariable String experimentId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String control, @RequestBody List<String> taskIds);
}
