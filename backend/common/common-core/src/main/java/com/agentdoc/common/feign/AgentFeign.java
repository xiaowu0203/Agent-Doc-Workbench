package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "agent-service", url = "${agent-doc.feign.gateway-url:http://localhost:9090}")
public interface AgentFeign {

    /**
     * 根据AgentId查询Agent执行配置文件
     * @param agentId AgentId
     * @return Agent执行配置文件
     */
    @GetMapping("/api/agent/internal/agents/{agentId}/execution-profile")
    Result<AgentExecutionProfileVO> getExecutionProfile(@PathVariable Long agentId);
}
