package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.AgentOnlineConfigPrepareDTO;
import com.agentdoc.common.feign.vo.AgentOnlineConfigPairVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/** 线上模板直连 Agent；仍透传用户身份并校验 OWNER/动作，不经公共网关。 */
@FeignClient(name = "agent-online-config", url = "${agent-doc.online.agent-url:http://localhost:8084}")
public interface AgentOnlineConfigFeign {
    @PostMapping("/internal/online-configs/prepare")
    Result<AgentOnlineConfigPairVO> prepareOnlineConfigs(@RequestBody AgentOnlineConfigPrepareDTO request);

    @GetMapping("/internal/online-configs/{experimentId}/dependency")
    Result<String> onlineDependency(@PathVariable Long experimentId, @RequestParam Long spaceId);
}
