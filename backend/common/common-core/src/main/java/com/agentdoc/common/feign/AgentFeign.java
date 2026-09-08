package com.agentdoc.common.feign;

import com.agentdoc.common.api.Result;
import com.agentdoc.common.feign.dto.AgentBatchQueryDTO;
import com.agentdoc.common.feign.dto.AgentTaskOptionQueryDTO;
import com.agentdoc.common.feign.vo.AgentExecutionProfileVO;
import com.agentdoc.common.feign.vo.AgentExecutionAuditVO;
import com.agentdoc.common.feign.vo.AgentExecutionTokenUsageVO;
import com.agentdoc.common.feign.vo.AgentRefVO;
import com.agentdoc.common.feign.vo.AgentTaskOptionVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "agent-service", url = "${agent-doc.feign.gateway-url:http://localhost:9090}")
public interface AgentFeign {

    /**
     * 根据AgentId查询Agent执行配置文件
     * @param agentId AgentId
     * @return Agent执行配置文件
     */
    @GetMapping("/api/agent/internal/agents/{agentId}/execution-profile")
    Result<AgentExecutionProfileVO> getExecutionProfile(@PathVariable Long agentId);

    /**
     * 批量查询 Agent 最小展示信息。
     */
    @PostMapping("/api/agent/internal/agents/refs/query")
    Result<List<AgentRefVO>> queryAgentRefs(@RequestBody AgentBatchQueryDTO request);

    /**
     * 查询指定文档可用于创建任务的已启用 Agent。
     */
    @PostMapping("/api/agent/internal/agents/task-options/query")
    Result<List<AgentTaskOptionVO>> queryTaskOptions(@RequestBody AgentTaskOptionQueryDTO request);

    /**
     * 按工作台任务查询脱敏执行审计。
     */
    @GetMapping("/api/agent/executions/by-task/{taskId}")
    Result<AgentExecutionAuditVO> getExecutionAudit(@PathVariable Long taskId,
                                                    @RequestParam Long spaceId);

    /** 内部同步用 Token 投影，不要求用户登录且不暴露执行审计明细。 */
    @GetMapping("/api/agent/internal/executions/by-task/{taskId}/token-usage")
    Result<AgentExecutionTokenUsageVO> getExecutionTokenUsage(@PathVariable Long taskId);
}
