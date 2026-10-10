package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.OnlineAssignmentRequestDTO;
import com.agentdoc.common.feign.vo.OnlineTaskDispatchProofVO;
import com.agentdoc.common.feign.vo.OnlineTaskFactVO;
import java.util.List;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import org.springframework.http.HttpHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** Task 所有的创建意图证明；实际用户身份由既有拦截器透传。 */
@FeignClient(name = "online-task", url = "${agent-doc.online.task-url:http://localhost:8083}")
public interface OnlineTaskFeign {
    @PostMapping("/api/task/internal/online-observation/{experimentId}/repair")
    Result<Void> repair(@PathVariable String experimentId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String control, @RequestBody List<String> taskIds);
    @PostMapping("/api/task/internal/online-observation/{experimentId}/facts")
    Result<List<OnlineTaskFactVO>> facts(@PathVariable String experimentId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String token, @RequestBody List<String> taskIds);
    @PostMapping("/api/task/internal/online-observation/{experimentId}/cancel")
    Result<Void> cancel(@PathVariable String experimentId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String token, @RequestBody List<String> taskIds);
    @GetMapping("/api/task/internal/online-release")
    Result<String> release(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String control);
    @GetMapping("/api/task/internal/online-dispatch/{taskId}/execution-identity")
    Result<String> executionIdentity(@PathVariable String taskId, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
    @GetMapping("/api/task/internal/online-creation-intents/{taskId}")
    Result<OnlineAssignmentRequestDTO> creationProof(@PathVariable String taskId);
    @GetMapping("/api/task/internal/online-dispatch/{taskId}/human-proof")
    Result<OnlineTaskDispatchProofVO> humanDispatchProof(@PathVariable String taskId);
    @GetMapping("/api/task/internal/online-dispatch/{taskId}/wait-proof")
    Result<OnlineTaskDispatchProofVO> waitDispatchProof(@PathVariable String taskId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String wait);
}
