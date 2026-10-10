package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.constant.OnlineCapabilityConstant;
import com.agentdoc.common.feign.vo.OnlineExecutionFactVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Agent 接收窄批次授权，只有既存执行观察和取消请求。 */
@FeignClient(name = "online-agent", url = "${agent-doc.online.agent-url:http://localhost:8084}")
public interface OnlineAgentFeign {
    @GetMapping("/api/agent/internal/online-config-observation/{experimentId}/dependency")
    Result<String> dependency(@PathVariable String experimentId, @RequestHeader(OnlineCapabilityConstant.HEADER) String control);
    @PostMapping("/api/agent/internal/online-observation/{experimentId}/facts")
    Result<List<OnlineExecutionFactVO>> facts(@PathVariable String experimentId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String token, @RequestBody List<String> taskIds);
    @PostMapping("/api/agent/internal/online-observation/{experimentId}/cancel")
    Result<Void> cancel(@PathVariable String experimentId,
            @RequestHeader(OnlineCapabilityConstant.HEADER) String token, @RequestBody List<String> taskIds);
}
